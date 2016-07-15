package me.serce.franky.ui.flame

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.EmptySubstitutor
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.components.BorderLayoutPanel
import me.serce.franky.Protocol.CallTraceSampleInfo
import me.serce.franky.Protocol.MethodInfo
import me.serce.franky.ui.flame.NullPsiMethod.NULL_PSI_METHOD
import rx.lang.kotlin.PublishSubject
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.event.ActionEvent
import java.text.NumberFormat
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent

fun CallTraceSampleInfo.validate() {
    if (frameList.isEmpty()) {
        throw IllegalArgumentException("Empty trace sample $this")
    }
}

class FlameTree(val sampleInfo: List<CallTraceSampleInfo>) {
    val root: FlameNode = FlameNode(0, null)

    init {
        for (sample in sampleInfo) {
            sample.validate()
            addSampleToTree(sample)
        }
    }

    private fun addSampleToTree(sample: CallTraceSampleInfo) {
        val coef = sample.callCount

        var node = root
        for (frame in sample.frameList.reversed()) {
            val methodId = frame.jMethodId
            node = node.children.computeIfAbsent(methodId, {
                FlameVertex(coef, FlameNode(frame.jMethodId, node))
            }).node
        }
        node.selfCost += coef
    }
}

data class FlameVertex(var cost: Int, val node: FlameNode)

class FlameNode(val methodId: Long, val parent: FlameNode?) {
    var selfCost: Int = 0;
    val children: HashMap<Long, FlameVertex> = hashMapOf()
}


//////////////// components

class FlameComponent(private val tree: FlameTree, val frameFactory: (Long) -> MethodInfo?) : JComponent() {
    private data class ComponentCoord(val x: Double, val width: Double, val level: Int, val parentWidth: Int) {
        companion object {
            const val frameHeight = 20
        }

        fun getX() = (x * parentWidth).toInt()
        fun getWidth() = (width * parentWidth).toInt()
        fun getY() = level * frameHeight
        fun getHeight() = frameHeight
    }

    val methodInfoSubject = PublishSubject<MethodInfo>()
    private val nodeToComp = hashMapOf<FlameNode, FrameComponent>()
    var currentRoot = tree.root
    var maxHeight = 0

    init {
        layout = null
        recalcPositions()
    }

    private fun build(node: FlameNode, begin: Double, end: Double, level: Int) {
        val width = end - begin
        if (width <= 0) {
            return
        }
        recalculateComponentCoords(begin, level, node, width, false)

        val totalCost = node.selfCost + node.children.map { it.value.cost }.sum()
        var nodeBegin = begin
        for ((id, vertex) in node.children) {
            val nodeWidth = width * (vertex.cost / totalCost.toDouble())
            build(vertex.node, nodeBegin, nodeBegin + nodeWidth, level + 1)
            nodeBegin += nodeWidth
        }
    }

    private fun recalculateComponentCoords(begin: Double, level: Int, node: FlameNode, width: Double, collapsed: Boolean) {
        val component = nodeToComp.getOrPut(node, { makeFrameComponent(node, width) })
        val coord = ComponentCoord(begin, width, level, getWidth())
        component.apply {
            size = Dimension(coord.getWidth(), coord.getHeight())
            location = Point(coord.getX(), coord.getY())
            if(collapsed) {
                border = BorderFactory.createLineBorder(Color.RED)
            } else {
                border = BorderFactory.createLineBorder(Color.BLACK)
            }
        }
        if (coord.getY() >= maxHeight) {
            maxHeight = coord.getY()
        }
    }

    private fun calcTop(currentRoot: FlameNode): Int {
        fun countLevel(currentRoot: FlameNode): Int {
            var l = 0
            var curNode = currentRoot.parent
            while (curNode != null) {
                l++; curNode = curNode.parent
            }
            return l
        }

        val allLevel = countLevel(currentRoot)
        var level = allLevel
        var node: FlameNode? = currentRoot
        while (node?.parent != null) {
            recalculateComponentCoords(0.0, --level, node!!.parent!!, 1.0, true)
            node = node.parent
        }
        return allLevel
    }

    private fun makeFrameComponent(node: FlameNode, percentage: Double): FrameComponent {
        val methodInfo = frameFactory(node.methodId) ?: rootMethodInfo()
        return FrameComponent(methodInfo, percentage).apply {
            this@FlameComponent.add(this)
            expandPublisher.subscribe {
                resetNode(node)
            }
        }
    }

    private fun resetToRoot() = resetNode(tree.root)

    private fun resetNode(node: FlameNode) {
        currentRoot = node
        for (c in components) {
            c.size = Dimension(0, 0)
            c.location = Point(0, 0)
        }
        recalcPositions()
        validate()
        repaint()
    }



    override fun paintComponent(g: Graphics) {
        recalcPositions()
        super.paintComponent(g)
    }

    /**
     * hack for null layout
     */
    override fun getPreferredSize() = super.getPreferredSize().apply {
        height = maxHeight
    }

    private fun recalcPositions() {
        maxHeight = 0
        val level = calcTop(currentRoot)
        build(currentRoot, 0.0, 1.0, level)
    }

    private fun rootMethodInfo() = MethodInfo.newBuilder().setJMethodId(0).setHolder("").setName("all").setSig("").setCompiled(false).build()
}

//////


class FrameComponent(val methodInfo: MethodInfo, val percentage: Double) : BorderLayoutPanel() {
    companion object {
        val methodToPsiCache = HashMap<Long, PsiMethod>()
        val percentFormat = NumberFormat.getPercentInstance(Locale.US).apply {
            maximumFractionDigits = 2
        }
    }

    val expandPublisher = PublishSubject<ActionEvent>()
    val psiMethod: PsiMethod

    init {
        psiMethod = methodToPsiCache.getOrPut(methodInfo.jMethodId, {
            findPsiMethod() ?: NULL_PSI_METHOD
        })
    }


    private val expandBtn = JButton("expand").apply {
        addActionListener { expandPublisher.onNext(it) }
    }

    private val methodBtn = JBLabel("${getMethodName()} (${percentFormat.format(percentage)})").apply {

        //        addActionListener {
        //            click()
        //        }
    }

    private fun click() {
        val method = findPsiMethod()
        method?.navigate(true)
    }

    private fun getMethodName() = when (psiMethod) {
        NULL_PSI_METHOD -> formatMethodInfo()
        else -> PsiFormatUtil.formatMethod(psiMethod, EmptySubstitutor.EMPTY,
                PsiFormatUtilBase.SHOW_NAME or
                        PsiFormatUtilBase.SHOW_FQ_NAME or
                        PsiFormatUtilBase.SHOW_PARAMETERS or
                        PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                PsiFormatUtilBase.SHOW_TYPE)
    }

    private fun formatMethodInfo() = "${methodInfo.holder}.${methodInfo.name}"

    private fun findPsiMethod(): PsiMethod? {
        if (ApplicationManager.getApplication() == null) {
            return null;
        }
        val projectManager = ProjectManager.getInstance()
        for (project in projectManager.openProjects) {
            val psiManager = PsiManager.getInstance(project)
            return ClassUtil.findPsiClass(psiManager, methodInfo.holder)
                    ?.findMethodsByName(methodInfo.name, false)
                    ?.find {
                        methodInfo.sig == JVMNameUtil.getJVMSignature(it).getName(null)
                    }
        }
        return null
    }

    init {
        addToCenter(methodBtn)
        addToRight(expandBtn)
    }
}