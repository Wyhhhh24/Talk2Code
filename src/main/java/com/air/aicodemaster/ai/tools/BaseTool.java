package com.air.aicodemaster.ai.tools;


import cn.hutool.json.JSONObject;

/**
 * 工具基类，抽象类
 * 定义所有工具的通用接口
 * 在开发这些؜工具时，必须特别注意文件操作的安全性‌，仔细检查可操作的路径范围，避免误操‍作系统重要文件
 */
public abstract class BaseTool {

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 当大模型想要调用工具时，通过事件监听，框架会执行这个方法
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 当大模型想要调用工具时，通过事件监听，框架会执行这个方法
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);
}
