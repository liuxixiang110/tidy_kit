package com.tidy.kit.ui;

import com.intellij.util.ui.JBUI;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseEvent;

/**
 * 可拖拽换序的标签容器。
 * JTabbedPane 没有 moveTab 这样的 API,所以把"重排"收成一个原语 {@link #moveTab(int, int)},
 * 拖拽手势只是它的触发器:按住标签头越过相邻标签的中线就立即换位,所见即结果。
 *
 * 刻意用手动拖拽而不是 AWT 的 DragSource/DropTarget:实时重排会把正在拖的标签头 remove 再 add,
 * 与 DnD 会话对同一组件的所有权假设冲突,而且传一个下标还得为它写 Transferable。
 */
class ReorderableTabbedPane extends JTabbedPane {

    private final Cursor defaultCursor = Cursor.getDefaultCursor();
    private final Cursor moveCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

    ReorderableTabbedPane() {
        super(JTabbedPane.TOP);
    }

    /** 让某个标签头可以被拖动换序;由建头的一方在 setTabComponentAt 之后调用。
     * mouseListener 与 mouseMotionListener 必须共用一个实例,否则 dragging 状态在按下与拖动之间不同步 */
    void installDragReorder(Component header) {
        HeaderDragHandler handler = new HeaderDragHandler(header);
        header.addMouseListener(handler);
        header.addMouseMotionListener(handler);
    }

    /**
     * 把 from 位置的标签移到 to 位置(下标语义与拖拽悬停一致:移到 to 那个标签所在的位置)。
     * 只改挂载关系,内容面板始终是同一个实例,所以输入文本、结果树、展开状态都不受影响。
     */
    void moveTab(int from, int to) {
        int count = getTabCount();
        if (from < 0 || from >= count || from == to) {
            return;
        }
        Component content = getComponentAt(from);
        String title = getTitleAt(from);
        Component header = getTabComponentAt(from);
        // 换位过程中正在看的面板不能被甩掉,否则拖一下内容区就空了
        Component showing = getSelectedComponent();

        remove(from);
        // 目标下标按“结果里排第 to”理解:先移除后数组短一个,直接夹到 [0, count-1] 插入即可。
        // 不能先把 to 减一:那样 moveTab(c, c+1) 会变成原地不动,向右拖一个标签就永远拖不动。
        int target = Math.max(0, Math.min(to, getTabCount()));
        insertTab(title, null, content, null, target);
        if (header != null) {
            setTabComponentAt(target, header);
        }
        if (showing != null) {
            setSelectedComponent(showing);
        }
    }

    /** 标签头实例对应的下标;实时重排时下标一直在变,所以只认实例身份 */
    private int indexOfHeader(Component header) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getTabComponentAt(i) == header) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 单个标签头的拖拽状态机。按下记的是 header 实例而不是下标,
     * 因为一次拖动中可能连续跨过好几个标签,下标每次都失效。
     */
    private final class HeaderDragHandler extends MouseInputAdapter {
        /** 超过该位移才算拖拽,免得正常点选标签被当成拖动 */
        private final int dragThreshold = JBUI.scale(5);

        private final Component header;
        private Point pressOnScreen;
        private boolean dragging;

        HeaderDragHandler(Component header) {
            this.header = header;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            // 从关闭按钮上起手不算拖拽
            if (SwingUtilities.getAncestorOfClass(JButton.class, e.getComponent()) != null) {
                return;
            }
            pressOnScreen = e.getLocationOnScreen();
            dragging = false;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
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
            int current = indexOfHeader(header);
            if (current < 0) {
                return;
            }
            // 鼠标可能落在子组件(标签标题)上,统一换算到本容器坐标再判下标
            Point inPane = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), ReorderableTabbedPane.this);
            int hover = indexAtLocation(inPane.x, inPane.y);
            if (hover >= 0 && hover != current) {
                moveTab(current, hover);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragging) {
                dragging = false;
                setCursor(defaultCursor);
                revalidate();
                repaint();
            }
            pressOnScreen = null;
        }
    }
}
