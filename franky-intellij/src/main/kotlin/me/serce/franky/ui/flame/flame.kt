package me.serce.franky.ui.flame

import com.google.protobuf.CodedInputStream
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.ClassUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import gnu.trove.TIntObjectHashMap
import me.serce.franky.Protocol
import me.serce.franky.Protocol.*
import rx.lang.kotlin.PublishSubject
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.FileInputStream
import java.util.*
import javax.swing.*

fun CallTraceSampleInfo.validate() {
    if (frameList.isEmpty()) {
        throw IllegalArgumentException("Empty trace sample $this")
    }
}

class FlameTree(val sampleInfo: List<CallTraceSampleInfo>) {
    val root: FlameNode = FlameNode(0)

    init {
        for (sample in sampleInfo) {
            sample.validate()
            addSampleToTree(sample)
        }
    }

    private fun addSampleToTree(sample: CallTraceSampleInfo) {
        val coef = sample.callCount

        var node = root
        for (frame in sample.frameList) {
            val methodId = frame.jMethodId
            node = node.children.computeIfAbsent(methodId, {
                FlameVertex(coef, FlameNode(frame.jMethodId))
            }).node
        }
        node.selfCost += coef
    }
}

data class FlameVertex(var cost: Int, val node: FlameNode)

class FlameNode(val methodId: Long) {
    var selfCost: Int = 0;
    val children: HashMap<Long, FlameVertex> = hashMapOf()
}


//////////////// components

class FlameComponent(private val tree: FlameTree, val frameFactory: (Long) -> MethodInfo?) : JComponent() {
    data class ComponentCoord(val x: Double, val width: Double, val level: Int, val parentWidth: Int) {
        companion object {
            const val frameHeight = 20
        }

        fun getX() = (x * parentWidth).toInt()
        fun getWidth() = (width * parentWidth).toInt()
        fun getY() = level * frameHeight
        fun getHeight() = frameHeight
    }

    val methodInfoSubject = PublishSubject<MethodInfo>()
    private val nodeToComp = hashMapOf<FlameNode, JComponent>()
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
        val coord = ComponentCoord(begin, width, level, getWidth())
        nodeToComp.getOrPut(node, { makeFrameComponent(node) })
                .apply {
                    size = Dimension(coord.getWidth(), coord.getHeight())
                    location = Point(coord.getX(), coord.getY())
                }
        if (coord.getY() >= maxHeight) {
            maxHeight = coord.getY()
        }

        val totalCost = node.selfCost + node.children.map { it.value.cost }.sum()
        var nodeBegin = begin
        for ((id, vertex) in node.children) {
            val nodeWidth = width * (vertex.cost / totalCost.toDouble())
            build(vertex.node, nodeBegin, nodeBegin + nodeWidth, level + 1)
            nodeBegin += nodeWidth
        }
    }

    private fun makeFrameComponent(node: FlameNode): FrameComponent {
        val methodInfo = frameFactory(node.methodId) ?: rootMethodInfo()
        return FrameComponent(methodInfo).apply {
            this@FlameComponent.add(this)
            expandPublisher.subscribe {
                resetNode(node)
            }
        }
    }

    private fun resetToRoot() {
        currentRoot = tree.root
    }

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
        build(currentRoot, 0.0, 1.0, 0)
    }

    private fun rootMethodInfo() = MethodInfo.newBuilder().setJMethodId(0).setHolder("").setName("").setSig("").build()
}


class FrameComponent(val methodInfo: MethodInfo) : BorderLayoutPanel() {
    val expandPublisher = PublishSubject<ActionEvent>()

    private val expandBtn = JButton("expand").apply {
        addActionListener { expandPublisher.onNext(it) }
    }
    private val methodBtn = JButton(methodInfo.name.toString()).apply {
        addActionListener {
            click()
        }
    }

    private fun click() {
        val projectManager = ProjectManager.getInstance()
        val projects = projectManager.openProjects
        if (projects.isNotEmpty()) {
            val psiManager = PsiManager.getInstance(projects.first())
            val method = ClassUtil.findPsiClass(psiManager, methodInfo.holder)
                    ?.findMethodsByName(methodInfo.name, false)
                    ?.filter {
                        methodInfo.sig == JVMNameUtil.getJVMSignature(it).getName(null)
                    }
                    ?.firstOrNull()
            method?.navigate(true)
        }
    }

    init {
        addToCenter(methodBtn)
        addToRight(expandBtn)
    }
}


fun main(args: Array<String>) {
    SwingUtilities.invokeLater {
        val result = Protocol.Response.parseFrom(CodedInputStream.newInstance(FileInputStream("/home/serce/tmp/ResultData")))
        val profInfo = result.profInfo
        val samples = profInfo.samplesList
        val methods: Map<Long, MethodInfo> = profInfo.methodInfosList.associateBy({ it.jMethodId }, { it })


        val tree = FlameTree(samples)
        val panel = JFrame().apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            pack()
            size = Dimension(800, 600)
            contentPane.apply {
                add(JScrollPane(FlameComponent(tree, { methods[it] }).apply {
                    size = Dimension(800, 600)
                    methodInfoSubject.subscribe {
                        println("CLICK $it")
                    }
                }).apply {
                    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
                })
            }
            isVisible = true
            repaint()
        }
    }
}