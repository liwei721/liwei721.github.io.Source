---
title: Adb工作原理
date: 2017-02-08 19:32:09
tags: adb通信原理 adbserver
categories: Android技术
---
## 引言
- Adb是Android Debug bridge的简称，Android的初衷是用adb这样的一个工具来协助开发人员在开发android应用的过程中更快更好的调试apk，因此adb具有安装卸载apk、拷贝推送文件、查看设备硬件信息、查看应用程序占用资源、在设备执行shell命令等功能。
- 对于和Android打交道的同学，对Adb一定不陌生，每天的工作基本都离不开它，最近有时会碰到Adb的问题，比如adb server没有响应之类的，因此就想弄清楚Adb的工作原理到底是怎么样的？这样再碰到问题就能知道大概该怎么样去解决。
- 其实之前看过adb的源码，但是时间久远，看过的东西都忘记了，只能从头来了解。这次写下来，便于自己查看，更为了能跟感兴趣的同学交流。
- 我这里就不再去扣源码（源码在system/core/adb）了，主要是以介绍原理为主。

## 概念介绍
### Adb 是C/S架构，要了解其工作原理，其实就是要搞清楚下面三个概念：
- adb server
- adb client
- adb deamon

接下来就分开介绍下他们的原理
#### adb server
- ADB Server是运行在主机上的一个后台进程。它有两个作用：1）检测USB端口感知设备的连接和拔除，以及模拟器实例的启动或停止；2）将adb client的请求通过usb或者tcp的方式发送到对应的adbd上。
- ADB server默认监听5037端口，我们可以通过在cmd命令行输入netstat -nao | findStr 5037 来查看哪个pid的进程在监听5037，我电脑上的pid = 13740，然后再通过tasklist来查看13740对应的应用程序是啥？如下图可以看到是adb.exe（就是adb server）。
![Adb server的进程](/upload/image/zlw/adb_tasklist.png)

#### adb client
- adb client 可以是命令行，也可能是DDMS等工具。它运行在开发用的电脑上。
- 它主要的工作是：解析像：push、shell、install等命令的参数，做必要预处理，然后转移为指令或数据，发送给adb Server。
- 我们在命令行执行命令时，会创建一个adb.exe的client，命令执行完后就被销毁掉。（可以在PC的任务管理器中查看）
- 当启动adb客户端时，客户端首先检测adb服务端进程是否运行，如果没有运行，则启动服务端。当服务端启动时，它会绑定到本地的5037端口，并且监听从adb client发来的命令——所有的adb客户端都使用5037端口与adb服务端通信。

#### adb deamon(adbd)
- adbd 是运行在Android设备（真机/模拟器）后台的一个进程，它是由init进程启动的，并且系统一开机就已经启动。
- adbd存在于设备/sbin(我的目录是sbin，也可能是xbin)目录下。如下图所示：
![adbd进程](/upload/image/zlw/adbd_sbin.png)
- 它的主要作用是处理来自 adb server的命令行请求，然后获取对应Android设备的信息，再将结果返回给adb server。

## 通信原理
### 清楚了上面的三个概念，相信对他们的通信原理应该已经有了比较清楚的认识。对熟悉他们通信原理就比较容易了。
#### 流程图
![概要流程图](/upload/image/zlw/Adb_logic.png)
![具体流程图](/upload/image/zlw/adb_logic2.png)

- 第一张图是个概要的流程图，大致介绍了他们之间的通信流程。
- 第二张图更详细的介绍了通信流程，包括有哪些线程及adbd位于Android系统的内核中、在DDMS中经常看到的JDWP线程等等。
- 第二张图提到了TCP/Ip 和USb 两种通信方式，其中前者用于模拟器的通信（真机wifi连接），后者用于与真机的通信。

#### 流程用文字描述
- client调用某个adb命令，比如adb push。
- adb进程fork出一个子进程作为server（PC端的常驻进程）
- server查找当前连接的emulator/device（查找客户端，为通信做准备）
- server接收到来自client请求。（用户在命令行或者通过工具执行命令）
- server处理请求，将本地处理不了的请求发给emulator/device（本地能处理的请求比如：adb devices 等）
- 位于emulator/device的adbd拿到请求后交给对应的java虚拟机进程（adbd收到请求会搞一个读和一个写进程用于收和回信息）。
- adbd将结果发回给server
- server讲结果发回给client（在工具中或者命令行界面展示结果）

## 常见问题

- The connection to adb is down, and a severe error has occured.You must restart adb and Eclipse.Please ensure that adb is correctly located at 'adb.exe' and can be executed
>- 重启下adb server   （adb kill-server adb start-server）

- ADB server didn't ACK * failed to start daemon *
>- 在任务管理器中查找5037端口被谁占用了，然后结束掉。

- 待不断补充……………………
