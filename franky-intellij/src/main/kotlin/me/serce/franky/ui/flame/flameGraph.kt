package me.serce.franky.ui.flame

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.EmptySubstitutor
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.components.BorderLayoutPanel
import me.serce.franky.Protocol.MethodInfo
import me.serce.franky.ui.JBLabel
import me.serce.franky.ui.MouseClickListener
import me.serce.franky.ui.flame.NullPsiMethod.NULL_PSI_METHOD
import rx.lang.kotlin.PublishSubject
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.text.NumberFormat
import java.util.*
import javax.swing.*

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

        val totalCost = node.cost
        var nodeBegin = begin
        for ((id, vertex) in node.children) {
            val nodeWidth = width * (vertex.cost / totalCost.toDouble())
            build(vertex, nodeBegin, nodeBegin + nodeWidth, level + 1)
            nodeBegin += nodeWidth
        }
    }

    private fun recalculateComponentCoords(begin: Double, level: Int, node: FlameNode, width: Double, collapsed: Boolean) {
        val component = nodeToComp.getOrPut(node, { makeFrameComponent(node, width) })
        val coord = ComponentCoord(begin, width, level, getWidth())
        component.apply {
            size = Dimension(coord.getWidth(), coord.getHeight())
            location = Point(coord.getX(), coord.getY())
//            background = when {
//                collapsed -> Color.BLACK
//                else -> null
//            }
//            isOpaque = true
            border = BorderFactory.createLineBorder(when {
                collapsed -> Color.RED
                else -> Color.BLACK
            })
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
        return FrameComponent(methodInfo, percentage, node.cost).apply {
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
}

private fun rootMethodInfo() = MethodInfo.newBuilder().setJMethodId(0).setHolder("reset").setName("all").setSig("").setCompiled(false).build()
fun MethodInfo.isRoot() = jMethodId == 0L


/**
 * Panel representing one cell (frame)
 */
class FrameComponent(val methodInfo: MethodInfo, percentage: Double, samplesCount: Int) : BorderLayoutPanel() {
    companion object {
        val methodToPsiCache = HashMap<Long, PsiMethod>()
        val percentFormat = NumberFormat.getPercentInstance(Locale.US).apply {
            maximumFractionDigits = 2
        }
    }

    val expandPublisher = PublishSubject<AWTEvent>()
    val psiMethod: PsiMethod

    init {
        psiMethod = methodToPsiCache.getOrPut(methodInfo.jMethodId, {
            findPsiMethod() ?: NULL_PSI_METHOD
        })
    }


    private val methodBtn = JBLabel(when {
        methodInfo.isRoot() -> "reset all"
        else -> "${getMethodName()} (${percentFormat.format(percentage)}, $samplesCount samples)"
    }).apply {
        //border = BorderFactory.createLineBorder(Color.RED)
        if (psiMethod != NULL_PSI_METHOD) {
            addMouseListener(MouseClickListener {
                click()
            })
            foreground = Color(18, 69, 120)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }
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
        addToCenter(JPanel().apply {
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = expandPublisher.onNext(e)

                override fun mouseEntered(e: MouseEvent) {
                    border = BorderFactory.createLineBorder(Color.DARK_GRAY, 1)
                }

                override fun mouseExited(e: MouseEvent) {
                    border = null
                }
            })
            add(methodBtn)
        })
        if (hasWarning()/* || true*/) {
            addToRight(createWarningLabel())
        }
    }

    private fun hasWarning() = !methodInfo.compiled && !methodInfo.isRoot()

    private fun createWarningLabel(): JLabel {
        var warningIcon = AllIcons.General.BalloonWarning
        if (warningIcon.iconHeight == 1) {
            // test mode
            warningIcon = ImageIcon(BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB))
        }
        return JBLabel {
            icon = warningIcon
            horizontalAlignment = JLabel.CENTER
            toolTipText = "Method hasn't been compiled"
        }
    }
}
