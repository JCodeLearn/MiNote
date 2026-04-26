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

// NetworkFailureException：自定义受检异常，用于标识同步过程中发生的网络通信失败
// 该类继承自 Exception（而非 RuntimeException），因此属于受检异常，
// 调用方必须显式捕获（try-catch）或在方法签名中声明抛出（throws）
package net.micode.notes.gtask.exception;

/**
 * NetworkFailureException：网络失败异常。
 * <p>
 * 当同步模块在与 Google Tasks API 进行网络通信时发生连接超时、服务器无响应、
 * 网络不可达等问题时，抛出此异常。与 ActionFailureException 的区别在于：
 * </p>
 * <ul>
 *   <li><b>NetworkFailureException</b>：侧重网络传输层面的失败（如 HTTP 请求失败）。</li>
 *   <li><b>ActionFailureException</b>：侧重业务逻辑执行层面的失败（如 JSON 解析失败）。</li>
 * </ul>
 * <p>
 * 由于继承自 Exception，抛出此异常会强制上层调用方必须处理，确保网络错误不会被忽略。
 * 同步控制器（如 GTaskManager）通常会捕获此异常并执行重试逻辑或通知用户网络不可用。
 * </p>
 */
public class NetworkFailureException extends Exception {

    // 序列化版本号：保证同一类的不同版本在序列化和反序列化时的兼容性
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 无参构造方法：创建不带任何错误信息的网络异常对象
     */
    public NetworkFailureException() {
        super();
    }

    /**
     * 带错误消息的构造方法
     *
     * @param paramString 错误描述信息，可通过 getMessage() 获取
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带错误消息和原始异常的构造方法（支持异常链）
     *
     * @param paramString    错误描述信息
     * @param paramThrowable 导致本次异常的原始异常对象（如 IOException）
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}