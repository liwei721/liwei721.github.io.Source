---
title: 安全扫描工具drozer的使用
date: 2017-03-01 15:07:17
tags: drozer, 安全扫描
categories: 测试工具
---

## 背景
- Drozer是一个由 MWR 安全团队维护开源的软件，该软件是针对Android平台的安全审计和攻击框架。安全人员可以通过drozer自身提供的一些module完成一些基础的安全测试功能，同时也可以根据需求实现自己的module，甚至可以在利用drozer提供的框架实现一些自动化审计功能。
- Drozer提供了一些工具来帮助你使用和分享Android上的公共利用漏洞。对于远程漏洞利用，它能生成shellcode来帮助你部署drozer代理端。

## 环境搭建
### PC客户端
- 下载drozer ：[drozer](https://labs.mwrinfosecurity.com/tools/drozer/)，选择适合自己系统的版本。我这里选择的是Windows版本[windows](https://github.com/mwrlabs/drozer/releases/download/2.3.4/drozer-installer-2.3.4.zip)。
- 下载完之后，直接安装就好，Windows双击exe文件。
- drozer没有界面，需要将drozer的安装目录配置到环境变量。因为后面要用drozer命令。
- 在下载的安装文件夹中，有一个agent.apk,是用于手机端的。

### 手机端
- 上面提到的agent.apk,直接命令行adb install agent.apk安装就行。
- 如果上述文件夹中没有agent.apk，可以下载一个[apk](https://github.com/mwrlabs/drozer/releases/download/2.3.4/drozer-agent-2.3.4.apk)
- 安装完apk后，打开应用，点击Embedded server开关，开启服务。

## 使用
### 创建连接
- 手机需要usb连接到PC，先用adb devices命令检查手机是否连上PC。
- 命令行执行adb forward tcp:31415 tcp:31415  ,adb forward的作用是将PC端31415端口的数据，转发到手机端31415端口，从而实现PC和手机的通信。
- 命令行执行 drozer console connect 用于连接手机端的agent，成功后会有如下图所示界面：
![drozer img](/upload/image/zlw/drozer.PNG)
- 如果碰到【Error 10054】之类的错误，检查下手机端的服务是否开启成功。

### 常用命令
#### 获取包名
- run app.package.list -f [app关键字]   查找某个app包名  -f[app关键字]用于通过关键字筛选

#### 获取应用基本信息
- run app.package.info -a packageName  返回对应包名的详细信息：权限、APKPATH等

#### 确定攻击面
- run app.package.attacksurface  packageName 查找攻击面，主要关注Android 固有的IPC通信机制的脆弱性，这些特点导致了这个App泄漏敏感信息给同一台设备上的其它App
- 我们首先运行上面这个命令确定哪些方面可能存在问题。
- 我们需要关注那些被export，但是并没有设置调用权限的组件：Activity、Service、BroadReceiver、ContentProvider

#### Activity
- run app.activity.info -a packageName ，上面命令可能找到了整体的攻击情况，这个命令用于进一步获取每个组件的攻击面信息，如activity
- 我们需要关注的是那些 除了首页Activity之外的所有【可导出且不需要权限的activity】，因为他们可以被随意的被其他应用启动。
- run  app.activity.start  --component packageName  ActivityName 用drozer来启动对应的activity

#### 检查Content Provider问题
##### 获取Content Provider信息
- run app.provider.info -a packageName 获取content provider的信息


##### Content Providers（数据泄露）
- run scanner.provider.finduris -a packageName 用于查找对应包名的可能存在问题的URI
- run app.provider.query --vertical URI        通过URI从content中获取信息，比如可以获取密码账号相关信息
- run scanner.provider.injection  -a  packageName   检测可注入的URI的注入点

##### Content Providers（SQL注入）
- run app.provider.query  URI --projection "'"  使用projection参数传递sql注入语句到content provider中
- run app.provider.query URI --projection "* from sqlite_master where type='table';--" --vertical    通过sql注入列出当前数据库DBContentProvider中的所有表名和字段名
- run app.provider.query URI --selection "'"  使用selection 参数传递sql注入语句到content provider中

##### 同时检测SQL注入和目录遍历
- run scanner.provider.injection -a packageName
- run scanner.provider.traversal -a packageName

#### intent组件触发（拒绝服务、权限提升）
##### 介绍名词
- 拒绝服务：应用在使用getIntent()，getAction()，Intent.getXXXExtra()获取到空数据、异常或者畸形数据时没有进行异常捕获，应用就会发生Crash，应用不可使用（本地拒绝服务）。有些恶意应用可通过向受害者应用发送此类空数据、异常或者畸形数据从而使应用产生本地拒绝服务
- 权限提升：

##### 查看暴露的广播组件信息
- run app.broadcast.info -a com.package.name　　获取broadcast receivers信息
- run app.broadcast.send --component 包名 --action android.intent.action.XXX

##### 尝试拒绝服务攻击检测，向广播组件发送不完整intent（空action或空extras）
- run app.broadcast.send 通过intent发送broadcast receiver
######  空action
- run app.broadcast.send --component 包名 ReceiverName
###### 空extras
- run app.broadcast.send --action android.intent.action.XXX

#### 检查service问题
- run app.service.info -a packageName

#### 文件操作
- run scanner.misc.writablefiles --privileged /data/data/com.sina.weibo
- run scanner.misc.readablefiles --privileged /data/data/com.sina.weibo

#### 其他
- 我这里只是列出了命令，并没有结合具体的例子，大家可以参考[drozer的使用](http://www.cnblogs.com/1chavez/p/4492574.html)，这里的有一些更具具体的例子。
- 这里的大多数命令都是从其他地方copy过来的，我自己实践了一部分。这里也是为了记录下来预防之后会需要。

## 总结
- 安全扫描，对APK来说非常重要，因为不经意的一个漏洞，可能会对用户的财产造成损失（因为手机上绑定了用户的银行卡等信息）。
- 后面随着对安全扫描的熟悉，考虑将规则添加到静态代码扫描规则中。
