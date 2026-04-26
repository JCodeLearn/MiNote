/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ActionFailureException：自定义运行时异常，用于标识同步过程中发生的可恢复动作失败
// 该类继承自 RuntimeException，因此属于非受检异常，调用方无需显式捕获
package net.micode.notes.gtask.exception;

/**
 * ActionFailureException：同步动作失败异常。
 * <p>
 * 当同步模块在执行某个具体动作（如生成 JSON、发送 HTTP 请求等）过程中遇到
 * 可预见的失败时，抛出此异常。与 NetworkFailureException 的区别在于：
 * ActionFailureException 侧重业务逻辑执行失败，而非网络通信层面的问题。
 * </p>
 * <p>
 * 由于继承自 RuntimeException，抛出此异常不会强制要求上层 try-catch，
 * 但同步控制器通常会统一捕获并进行错误处理（如标记同步失败、通知 UI 等）。
 * </p>
 */
public class ActionFailureException extends RuntimeException {

    // 序列化版本号：保证同一类的不同版本在序列化和反序列化时的兼容性
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 无参构造方法：创建不带任何错误信息的异常对象
     */
    public ActionFailureException() {
        super();
    }

    /**
     * 带错误消息的构造方法
     *
     * @param paramString 错误描述信息，可通过 getMessage() 获取
     */
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带错误消息和原始异常的构造方法（支持异常链）
     *
     * @param paramString    错误描述信息
     * @param paramThrowable 导致本次异常的原始异常对象
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}