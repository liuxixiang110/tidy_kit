package com.you.jsonformatter.history;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史持久化的 XML 载体,供 JsonHistoryStore 读写。
 * items 是单段 JSON 输入历史,pairs 是对比现场历史,两者共存同一份文件。
 */
public class HistoryState {

    public List<HistoryEntry> items = new ArrayList<>();

    /** 旧版本文件里没有该节点,反序列化时保持为空列表即可 */
    public List<DiffHistoryEntry> pairs = new ArrayList<>();
}
