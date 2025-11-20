# 数据库初始化
-- 创建库
create database if not exists yu_ai_code_mother;

-- 切换库
use yu_ai_code_mother;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;
-- editTime 和 updateTime 的区别：editTime 表示用户编辑个人信息的时间（需要业务代码来更新），而 updateTime 表示这条用户记录任何字段发生修改的时间（由数据库自动更新）
-- 给唯一值添加唯一键（唯一索引），比如账号 userAccount，利用数据库天然防重复，同时可以增加查询效率
-- 给经常用于查询的字段添加索引，比如用户昵称 userName，可以增加查询效率


-- 应用表
create table app
(
    id           bigint auto_increment comment 'id' primary key,
    appName      varchar(256)                       null comment '应用名称',
    cover        varchar(512)                       null comment '应用封面',
    initPrompt   text                               null comment '应用初始化的 prompt',
    codeGenType  varchar(64)                        null comment '代码生成类型（枚举）',
    deployKey    varchar(64)                        null comment '部署标识',
    deployedTime datetime                           null comment '部署时间',
    priority     int      default 0                 not null comment '优先级',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_deployKey (deployKey), -- 确保部署标识唯一
    INDEX idx_appName (appName),         -- 提升基于应用名称的查询性能
    INDEX idx_userId (userId)            -- 提升基于用户 ID 的查询性能
) comment '应用' collate = utf8mb4_unicode_ci;
-- 怎么知道某一个应用部署成功了？以及怎么区分输入哪个地址对应的是访问哪一个应用，这里我们可以创建一个 deployKey 字段
-- 由于每个网站应用文件的部署都是隔离的
-- 可以把每一个应用想象成一个隔离的沙箱，每个网站的应用文件一定是独立存储到一个目录里的，一定不能把多个网站的文件存到同一个目录，不好区分
-- deployKey 字段可以用来区分应用部署的地址，以及网站生成的位置，为了便于访问每个应用路径肯定不能太长
-- 参؜考美团 NoCode 等平台的设计，将 deployKey 设置为 6 位英文数‍字组成的唯一标识符
-- 需要用唯一字段来区分，可以作为应用的存储和访问路径；而且为了便于访问，每个应用的访问路径不能太长。

-- 这个设计中有几个值得注意的细节：
-- 1）priority 优先级字段：我们约定 99 表示精选应用，这样可以在主页展示高质量的应用，避免用户看到大量测试内容
-- 为什么用数字؜而不用枚举类型呢？原因是这样更利于扩展，比如‌约定 999 表示置顶；还可以根据数字灵活调‍整各个应用的具体展示顺序
-- 2）添加索引؜：给 deployKey、appName、userId 三个经常用于作为查询条件的字段增‍加索引，提高查询性能。
-- 注意，我们暂时؜不考虑将应用代码直接保存到数据库字段中，而是保存在文件系‌统里。
-- 这样可以避免数据库和文件存储不一致的问题，也便于后‍续扩展到对象存储等方案。
-- 3）deployedTime 部署时间，一个应用可以多次部署，这里就是该应用最近一次部署的时间


-- 对话历史表
create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     text                               not null comment '消息',
    messageType varchar(32)                        not null comment 'user/ai',
    appId       bigint                             not null comment '应用id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (appId),                       -- 提升基于应用的查询性能
    INDEX idx_createTime (createTime),             -- 提升基于时间的查询性能
    INDEX idx_appId_createTime (appId, createTime) -- 游标查询核心索引
) comment '对话历史' collate = utf8mb4_unicode_ci;
-- messageType 消息的类型，如果想要完整性一点可以把系统消息类别也加进来
-- 没有 editTime 字段，是一条消息基本不需要进行发送了然后进行二次编辑
-- 建立三个索引
-- 1.我们经常要根据 appId 去查询对话历史
-- 2.我们有可能是只按照时间去查询对话历史，比如说管理员，不管是哪个 appId ，我就想看用户的对话中有没有敏感信息或者做一些数据统计分析，加一个单独的创建时间索引
-- 3.联合索引，在游标查询中我们就是按照这两个字段作为 where 条件进行查询的，快速定位数据，减少了回表，性能高了很多
-- 扩展设计
-- 1）可以按需添加 parentId 字段，将 AI 消息和对应的用户提示词进行关联，便于生成失败时的重试、或者用户手动重新生成，也就是覆盖掉 AI 的响应进行回退。
-- 2）如果需要保存每个版本的代码文件，还可以添加 fileList 字段，结构为 JSON 数组格式，这样每条消息就对应一个代码版本。
-- 不过代码文件很大时，存到数据库里不是一个合适的选择。