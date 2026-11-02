package com.tidy.kit.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ReorderableTabbedPane.moveTab 的下标语义(设计:标签拖拽换序)。
 * 拖拽手势本身依赖真实鼠标轨迹,不做自动化测试,按本工程惯例在 runIde 沙盒手工验收。
 */
class ReorderableTabbedPaneTest {

    /** 工厂历次造出的标签头,用来验证重排后挂在新位置上的是新建实例而不是旧实例 */
    private final List<JLabel> createdHeaders = new ArrayList<>();

    /** n 个标签:标题 "0".."n-1",内容面板与标签头各是独立实例,便于按身份断言 */
    private ReorderableTabbedPane paneWith(int count) {
        ReorderableTabbedPane pane = new ReorderableTabbedPane();
        createdHeaders.clear();
        pane.setHeaderFactory(title -> {
            JLabel header = new JLabel("header-" + title);
            createdHeaders.add(header);
            return header;
        });
        for (int i = 0; i < count; i++) {
            pane.addTab(String.valueOf(i), new JPanel());
            pane.setTabComponentAt(i, new JLabel("initial-" + i));
        }
        return pane;
    }

    private static List<String> titles(ReorderableTabbedPane pane) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < pane.getTabCount(); i++) {
            out.add(pane.getTitleAt(i));
        }
        return out;
    }

    @Test
    void movesTabForward() {
        ReorderableTabbedPane pane = paneWith(3);
        pane.moveTab(0, 2);
        assertEquals(List.of("1", "2", "0"), titles(pane));
    }

    /** 拖拽每跨过一个标签中线就是一次相邻换位;这一步如果是个空操作,向右就永远拖不动 */
    @Test
    void adjacentSwapMovesRight() {
        ReorderableTabbedPane pane = paneWith(3);
        pane.moveTab(0, 1);
        assertEquals(List.of("1", "0", "2"), titles(pane));

        pane.moveTab(1, 2);
        assertEquals(List.of("1", "2", "0"), titles(pane));
    }

    @Test
    void movesTabBackward() {
        ReorderableTabbedPane pane = paneWith(3);
        pane.moveTab(2, 0);
        assertEquals(List.of("2", "0", "1"), titles(pane));
    }

    @Test
    void clampsOutOfBoundsTarget() {
        ReorderableTabbedPane toTail = paneWith(3);
        toTail.moveTab(0, 99);
        assertEquals(List.of("1", "2", "0"), titles(toTail));

        ReorderableTabbedPane toHead = paneWith(3);
        toHead.moveTab(2, -5);
        assertEquals(List.of("2", "0", "1"), titles(toHead));
    }

    /** 内容面板跟着走(它才是状态所在),标签头则重建一个新的 —— 刚被 remove 摘下来的组件不能再塞回去 */
    @Test
    void contentTravelsAndHeaderIsRebuilt() {
        ReorderableTabbedPane pane = paneWith(3);
        Object content = pane.getComponentAt(1);
        pane.moveTab(1, 0);

        assertSame(content, pane.getComponentAt(0));
        assertEquals(1, createdHeaders.size());
        assertSame(createdHeaders.get(0), pane.getTabComponentAt(0));
        assertEquals("header-1", ((JLabel) pane.getTabComponentAt(0)).getText());
    }

    @Test
    void noFactoryKeepsTitleAndContent() {
        ReorderableTabbedPane pane = new ReorderableTabbedPane();
        Component content = new JPanel();
        pane.addTab("A", content);
        pane.addTab("B", new JPanel());
        pane.moveTab(0, 1);
        assertEquals(List.of("B", "A"), titles(pane));
        assertSame(content, pane.getComponentAt(1));
    }

    @Test
    void ignoresNoOpAndIllegalSource() {
        ReorderableTabbedPane sameSlot = paneWith(3);
        sameSlot.moveTab(1, 1);
        assertEquals(List.of("0", "1", "2"), titles(sameSlot));

        ReorderableTabbedPane illegal = paneWith(3);
        illegal.moveTab(3, 0);
        illegal.moveTab(-1, 2);
        assertEquals(List.of("0", "1", "2"), titles(illegal));
    }

    /** 拖拽结束后用户看着的应该就是他刚拖的那个标签,不能因为换位而跳回另一个 */
    @Test
    void movedTabStaysSelected() {
        ReorderableTabbedPane pane = paneWith(3);
        Object moved = pane.getComponentAt(0);
        pane.setSelectedIndex(0);
        pane.moveTab(0, 2);
        assertSame(moved, pane.getSelectedComponent());
        assertEquals(2, pane.getSelectedIndex());
    }
}
