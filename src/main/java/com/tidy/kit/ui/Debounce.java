package com.tidy.kit.ui;

import javax.swing.Timer;

/**
 * 面板共用的防抖定时器工厂。
 * javax.swing.Timer 的 repeats 默认是 true,start 之后每隔 delay 就会一直触发,
 * 只有显式 setRepeats(false) 才是"停下来一段时间后再执行一次"的防抖语义。
 */
final class Debounce {

    private Debounce() {
    }

    /** 创建一次性定时器:restart() 后 delay 毫秒触发一次并自行停止 */
    static Timer oneShot(int delayMs, Runnable task) {
        Timer timer = new Timer(delayMs, e -> task.run());
        timer.setRepeats(false);
        return timer;
    }
}
