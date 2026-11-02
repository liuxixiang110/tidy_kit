package com.tidy.kit.ui;

import com.intellij.util.ui.JBUI;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseEvent;

/**
 * 可拖拽换序的标签容器。
 * JTabbedPane 没有 moveTab 这样的 API,所以把"重排"收成一个原语 {@link #moveTab(int, int)}:
 * 按住标签头拖动时只记录悬停目标,松手后才真正调用一次 moveTab。
 *
 * "鼠标还按着就不许改模型"是本类存在的核心约束,踩过的坑:标签头组件由 UI 私有的
 * TabContainer 承载,子组件事件往父容器冒泡的 dispatcher 链是 Container.addImpl 在
 * "加入那一刻"建立的;一边拖拽一边反复 remove/re-add 同一个标签头会把这条链弄断,
 * 现象是换完序之后点其他标签不再切换选中(点击事件再也到不了 JTabbedPane)。
 * 所以:1) 手势期间零模型变更,换位只在 mouseReleased 之后做一次;
 * 2) 换位时给被挪走的标签用 headerFactory 重建一个新标签头,而不是把刚被摘下来的
 *    那个再塞回去 —— 新建走的是与首次 addTab 完全相同的路径。
 *
 * 选中也不指望这条冒泡链:标签头收到 mousePressed 就直接 setSelectedIndex,
 * 否则能点中 tab 的区域只剩标签外围那圈内边距。
 *
 * 手势也不用 AWT 的 DragSource/DropTarget:传一个下标得为它写 Transferable,
 * 而且 DnD 会话同样对被拖组件有所有权假设。代价是没有跟随鼠标的拖拽影像。
 */
class ReorderableTabbedPane extends JTabbedPane {

    /** 为一个标签头提供视图;标题、关闭按钮、拖拽手势都由使用方在这里接好 */
    interface HeaderFactory {
        JComponent create(String title);
    }

    private final Cursor defaultCursor = Cursor.getDefaultCursor();
    private final Cursor moveCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    private HeaderFactory headerFactory;

    ReorderableTabbedPane() {
        super(JTabbedPane.TOP);
    }

    void setHeaderFactory(HeaderFactory factory) {
        this.headerFactory = factory;
    }

    /** 让某个标签头可以被拖动换序;由建头的一方在 setTabComponentAt 之后调用。
     * mouseListener 与 mouseMotionListener 必须共用一个实例,否则 dragging 状态在按下与拖动之间不同步 */
    void installDragReorder(Component header) {
        HeaderDragHandler handler = new HeaderDragHandler(header);
        header.addMouseListener(handler);
        header.addMouseMotionListener(handler);
    }

    /**
     * 把 from 位置的标签移到 to 位置,下标按"结果里排第 to"理解。
     * 内容面板始终是同一个实例,所以输入文本、结果树、展开状态都不受影响。
     */
    void moveTab(int from, int to) {
        int count = getTabCount();
        if (from < 0 || from >= count || from == to) {
            return;
        }
        Component content = getComponentAt(from);
        String title = getTitleAt(from);
        Component oldHeader = getTabComponentAt(from);

        remove(from);
        // 先移除后数组长一个,直接把 to 夹到 [0, count-1] 插入即可。
        // 不能先把 to 减一:那样 moveTab(c, c+1) 会变成原地不动,向右拖一个标签就永远拖不动。
        int target = Math.max(0, Math.min(to, getTabCount()));
        insertTab(title, null, content, null, target);
        // removeTabAt 已把旧标签头从 TabContainer 摘下,默认换一个新的,别复用刚被摘下来的组件
        if (headerFactory != null) {
            setTabComponentAt(target, headerFactory.create(title));
        } else if (oldHeader != null) {
            setTabComponentAt(target, oldHeader);
        }
        if (content != null) {
            setSelectedComponent(content);
        }
    }

    /** 标签头实例当前所在标签的下标 */
    private int indexOfHeader(Component header) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getTabComponentAt(i) == header) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 单个标签头的拖拽状态机。
     * 拖的是哪个标签,记的是它旁边的**内容面板**实例:标签头会在重排时被换掉,内容实例才稳定。
     */
    private final class HeaderDragHandler extends MouseInputAdapter {
        /** 超过该位移才算拖拽,免得正常点选标签被当成拖动 */
        private final int dragThreshold = JBUI.scale(5);

        private final Component header;
        private Point pressOnScreen;
        private Component draggedContent;
        private int hoverTarget = -1;
        private boolean dragging;

        HeaderDragHandler(Component header) {
            this.header = header;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            // 从关闭按钮上起手不算拖拽,也不当选中
            if (SwingUtilities.getAncestorOfClass(JButton.class, e.getComponent()) != null) {
                return;
            }
            // 按下就自己选中,不等事件冒泡到 pane:自定义标签头是 UI 私有 TabContainer 的孩子,
            // 指望父级命中测试( tabForCoordinate )会让可点区域只剩标签外围那圈内边距
            int pressed = indexOfHeader(header);
            if (pressed >= 0) {
                setSelectedIndex(pressed);
            }
            pressOnScreen = e.getLocationOnScreen();
            draggedContent = pressed < 0 ? null : getComponentAt(pressed);
            hoverTarget = -1;
            dragging = false;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (draggedContent == null) {
                return;
            }
            Point now = e.getLocationOnScreen();
            if (!dragging) {
                if (pressOnScreen == null
                        || Math.abs(now.x - pressOnScreen.x) < dragThreshold
                        && Math.abs(now.y - pressOnScreen.y) < dragThreshold) {
                    return;
                }
                dragging = true;
                setCursor(moveCursor);
            }
            // 鼠标可能落在子组件(标签标题)上,统一换算到本容器坐标再判下标
            Point inPane = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), ReorderableTabbedPane.this);
            int hover = indexAtLocation(inPane.x, inPane.y);
            if (hover >= 0) {
                // 只记目标,不改模型:悬停到标签条外(hover<0)则保留上一个目标
                hoverTarget = hover;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            int to = hoverTarget;
            Component content = draggedContent;
            boolean wasDragging = dragging;
            dragging = false;
            pressOnScreen = null;
            draggedContent = null;
            hoverTarget = -1;
            if (wasDragging) {
                setCursor(defaultCursor);
                if (content != null && to >= 0) {
                    // 等这次 release 在事件队列里走完(标签自己的选中逻辑)再动模型
                    SwingUtilities.invokeLater(() -> {
                        int from = indexOfComponent(content);
                        if (from >= 0) {
                            moveTab(from, to);
                        }
                    });
                }
            }
        }
    }
}
