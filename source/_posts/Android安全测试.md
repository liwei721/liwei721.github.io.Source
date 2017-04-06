---
title: Android安全测试
date: 2017-03-28 14:41
tags: android安全
categories: Android性能测试
---
## 背景
- apk的安全是应用的最后一道防线，当我们做好功能，成功发布之后，如果apk对安全考虑不周，会带来非常严重的后果。比如：通过发送不带extra的Intent，有可能使程序crash、再比如网络通信过程中使用了明文密码，可想而知后果会怎么样。
- 因此对apk的安全测试，是我们必须要考虑的事情。下面就介绍下一些常见的思路。
- 下面这张图是从TestHome上面盗来的，我觉得总结的很全面，涵盖了安全的很多方面。不过图中提到的乌云的连接都没法看了（乌云去年被关停了），可自行搜索查看。
![Android安全](/upload/image/zlw/安全图.jpg)

## Android组件安全性测试
- 上面有对Android各个组件的安全问题描述，大家可以对照看下各个组件可能会存在的攻击情况。
- 对于Android组件安全性的评估我们采用drozer。具体命令及使用参考[drozer使用](https://xdjatesterhome.github.io/2017/03/01/安全扫描工具drozer的使用/)

### drozer测试的一般思路
- 首先进入drozer模式，我就直接用命令来说明测试的思路。

``` bash
// 用于查看drozer所有的命令
$ list    

// 获取攻击面，即获取组件中哪些是可能被攻击的
$ run app.package.attacksurface packageName
比如，下面是我获取的一些内容
Attack Surface:
  1 activities exported
  2 broadcast receivers exported
  0 content providers exported
  2 services exported
    is debuggable

// 接着就是查看各个攻击面的详细情况（一般是组件被导出 export = true，且没有加访问权限控制）我这里就拿Activity举个例子，其他的参考上文提到的工具使用。
// 这里注意app的启动Activity一定是export = true的
$ run app.activity.info -a packageName
$ run.app.activity.start -component packageName activity   可以启动对应有攻击可能的Activity，看是否会有异常。

// 其他攻击面就顺着这个思路排查问题，ContentProvider需要注意的点比较多。

```
### 需要注意的一些点
- 有些组件虽然没有明确声明export = true，但是当添加了intent-filter 之后export默认就是true。
- 在AndroidManifest.xml中自定义权限时，要注意android:protectionLevel的值：
>1. normal：默认值，低风险权限，在安装的时候，系统会自动授予权限给 application。
>2. dangerous：高风险权限，如发短信，打电话，读写通讯录。使用此protectionLevel来标识用户可能关注的一些权限。Android将会在安装程序时，警示用户关于这些权限的需求，具体的行为可能依据Android版本或者所安装的移动设备而有所变化。
>3. signature： 签名权限，在其他 app 引用声明的权限的时候，需要保证两个 app 的签名一致。这样系统就会自动授予权限给第三方 app，而不提示给用户。
>4. signatureOrSystem：除了具有相同签名的APP可以访问外，Android系统中的程序有权限访问。
>5. 大部分开放的Provider，是提供给本公司的其他应用使用的，一般的话一个公司打包签名APP的签名证书都应该是一致的，这种情况下，Provider的android:protectionLevel应为设为“signature”。

### 问题判定的标准
- 通过drozer获取了攻击面，就能够知道哪些组件是export的，对于没必要export = true的组件是存在问题的（自己拿不准可以跟开发讨论下）。
- 对于需要export= true的组件，可以通过drozer发送启动组件或者模拟获取组件数据的请求，来查看app组件是否存在问题：是否会crash、是否可以拿到app数据库中的数据、是否可以监听到broadReceiver来获取action中的数据等等。
- 也可结合代码查看是否有潜在的问题存在。


### 问题解决
- 最直接的办法是export = false
- 对于需要export = true的，要做以下的处理：
>1. 保证对intent.getAction、intent.getAction.getExtra 做判null处理，避免其他程序恶意攻击，导致应用crash。
>2. 对service来说，当其他程序bindService时要做权限控制，即是当前应用的才返回service实例。避免敏感数据泄露，同时也要做好Service敏感数据的保护。
>3. 对于BroadcastReceiver，要对广播的发送者和接受者做限制：可以通过自定义权限，在AndroidManifest.xml中声明receiver时配置权限。（注意安全的等级）
>4. 对于ContentProvider我们可以参考[阿里安全ContentProvider分析](https://jaq.alibaba.com/community/art/show?articleid=352)，讲的很详细

- 总结来说通过export = false、加自定义权限等方式来解决组件的安全问题。

## Android /data/data/packageName目录
- 这里提到的/data/data/packageName 目录存放的是app常用的数据，比如：数据库、SharedPreferences及其他缓存数据，主要是关注数据是否存在泄露敏感数据的风险。比如：密码、包含敏感信息的文本等。
- 上面Android组件测试中提到的drozer测ContentProvider，也可以检测到一些通过ContentProvider访问数据库的问题。

### 测试方法
- 其实非常简单，在root过的手机上，直接去/data/data/packageName/ 目录查找类似databases、shared_prefs等目录，然后用adb pull命令将db文件拉到本地。
- 对于数据库db用SqliteManager（sqlite可视化工具）打开db，查看是否涉及隐私信息，我大致总结了如下：
>1. 是否涉及账号信息，且密码是否用明文保存。
>2. 是否涉及隐私内容信息，比如邮件正文等。
>3. 其他可能泄露的点。

- 对于shared_prefs及其他文本，可以直接用文本工具打开查看。


### 解决方案
- 对于隐私数据，可以进行加密。比如密码存储加密过后的。
- 还可以直接将数据库加密。

## 第三方扫描工具
- 现在有很多的扫描工具，我通过查资料，推荐几款用的较多的平台：
>1. [腾讯金刚](http://service.security.tencent.com/kingkong)
>2. [阿里聚安全](https://jaq.alibaba.com)  （查看详情需要通过签名认证app，且查看全部高危漏洞需要付费）
>3. [腾讯御安全](http://yaq.qq.com/)  (查看详情需要认证)
>4. [360显微镜](http://appscan.360.cn/)
>5. [爱内测](http://www.ineice.com/)    （无法查看详情）

- 不过平台扫出来的有些漏洞需要手动的去辨别是否是问题。

## 平台化
- 国内有一些公司基于[MobSf](https://github.com/liwei721/Mobile-Security-Framework-MobSF)来做自己的安全平台，这个后续可以作为一个研究内容。
- 抽空需要了解它的源码及原理。

## 参考资料
- [腾讯御安全技术博客](http://yaq.qq.com/blog)
- [360安全博客](http://appscan.360.cn/blog)
