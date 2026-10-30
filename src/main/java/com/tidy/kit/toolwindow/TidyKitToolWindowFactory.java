package com.tidy.kit.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.tidy.kit.ui.TidyKitTabs;
import com.tidy.kit.ui.JsonTreePanel;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;

public class TidyKitToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        TidyKitTabs tabs = new TidyKitTabs();
        JsonTreePanel first = tabs.firstPanel();
        if (first != null) {
            prefillFromClipboard(first);
        }

        // 内容标题与 plugin.xml 里的 toolWindow id 保持一致(改名时两处要一起改)
        Content content = ContentFactory.getInstance().createContent(tabs, "TidyKit", false);
        toolWindow.getContentManager().addContent(content);
    }

    /** 启动时自动填入剪贴板内容(设计 5.4,可关) */
    private void prefillFromClipboard(JsonTreePanel panel) {
        try {
            java.awt.datatransfer.Clipboard clipboard =
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            Object data = clipboard.getData(DataFlavor.stringFlavor);
            if (data instanceof String s && !s.isBlank()) {
                panel.setInputText(s);
            }
        } catch (Exception ignored) {
            // 剪贴板不可读时静默跳过
        }
    }
}
