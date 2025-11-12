package com.air.aicodemaster.ai.model;

import lombok.Data;

/**
 * 结构化输出，只需要在 AI 服务接口中，将返回值改成 Java 对象即可
 * 原理：框架会帮我们拼接提示词
 * 在原有提示词的基础上，会拼接类似的提示词，返回的结构：你必须输出如下格式：{"userName":"xxx","age":"xxx"}
 * 那么最后 AI 是不是就能够理解，最后输出的格式就是这样的
 * LanChain4j 框架只需要在我们刚刚生成的 AI 服务代理类中先取到 AI 的返回结果，然后发现要求返回的是一个对象
 * 在框架层面，用一个 Json 解析库解析成对象就好了
 * 框架自动帮我们做这些事情，自动拼接提示词，把 Json 转换成 java 对象
 *
 * 但是这种结构化输出，是通过拼接提示词的方式来实现的，但是 AI 不一定听话，AI 返回 Json 格式之后，需要把 Json 格式转换成 java 对象
 * 有时候 AI 返回的 Json 缺少了或者不完整，或者 AI 没有返回 Json ，框架不能正常解析就抛出异常
 */
@Data
public class HtmlCodeResult {

    private String htmlCode;

    private String description;
}
