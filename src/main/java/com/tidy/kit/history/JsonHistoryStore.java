package com.tidy.kit.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

import java.util.List;

/**
 * 应用级 JSON 历史,跨 IDE 重启持久化到 json-formatter-history.xml。
 * 两个池子共用一份文件:
 * - 单段输入历史(HistoryEntries),格式化面板与对比面板都会写入,任一输入框都能取用
 * - 对比现场历史(DiffHistoryEntries),只在对比面板写入与取用,一条包含 A、B 与模式
 * 纯逻辑(去重、上限、超长跳过)在上述两个容器,本类只负责平台注册与读写盘。
 *
 * 注意:解析成功的 JSON 会明文写入本机配置目录,含敏感字段的返回体请谨慎使用。
 */
@Service(Service.Level.APP)
@State(name = "JsonFormatterHistory", storages = @Storage("json-formatter-history.xml"))
public final class JsonHistoryStore implements PersistentStateComponent<HistoryState> {

    private final HistoryEntries entries = new HistoryEntries();
    private final DiffHistoryEntries diffPairs = new DiffHistoryEntries();

    public static JsonHistoryStore getInstance() {
        return ApplicationManager.getApplication().getService(JsonHistoryStore.class);
    }

    /** 解析成功时调用;空白与超长内容会被忽略 */
    public void record(String text) {
        entries.record(text, System.currentTimeMillis());
    }

    /** 对比成功时调用;任一侧空白或两侧合计超长会被忽略 */
    public void recordPair(String textA, String textB, String mode) {
        diffPairs.record(textA, textB, mode, System.currentTimeMillis());
    }

    /** 最新在前 */
    public List<HistoryEntry> list() {
        return entries.list();
    }

    /** 最新在前 */
    public List<DiffHistoryEntry> listPairs() {
        return diffPairs.list();
    }

    public void clear() {
        entries.clear();
    }

    public void clearPairs() {
        diffPairs.clear();
    }

    @Override
    public HistoryState getState() {
        HistoryState state = new HistoryState();
        state.items.addAll(entries.list());
        state.pairs.addAll(diffPairs.list());
        return state;
    }

    @Override
    public void loadState(HistoryState state) {
        entries.loadFrom(state.items);
        diffPairs.loadFrom(state.pairs);
    }
}
