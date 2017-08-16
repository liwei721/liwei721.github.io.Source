---
title: App耗电量测试工具之battery-historian
date: 2017-07-26 10:51:41
tags: battery-historian, 耗电量测试
categories: 测试工具
---

## 背景
- 在很久之前，写过一篇文章介绍过，怎么使用battery-historian第一版。不过现在battery-historian已经升级到了第二版，所以我们需要重新来介绍下这个工具。
- battery-historian的作用其实是对adb bugreport采集的数据进行聚合展示，方便我们排查电量相关问题。

##安装
- 由于battery-historian基于GO语言开发，因此我们需要先安装GO语言并配置环境：
>- 先去[GO官网](https://golang.org/dl/) 下载对应版本的安装包（一般我们是Windows）。然后一步步的安装完成即可。默认安装到C:\go，安装完成之后默认会在环境变量中新增GOROOT=C:\go。另外默认会将C:\go\bin添加到PATH中。
>- 在任意目录创建一个GO工作空间目录（随意命名），比如我创建的为E:\MineGo。
>- 然后在MineGo目录下创建三个子目录：src、pkg、bin。
>- 在环境变量中新增GOPATH（=E:\MineGo）和GOBIN（=E:\MineGo\bin），并且将GOBIN目录添加到PATH中。
>- 最后可以用go env命令查看配置的变量是否正确。

- 接下来还需要安装：Git、Python2.7（注意这里不要装Python3.x）、java。这里就不介绍了，因这几个工具都是非常常见的。
- 环境都配置好后，开始下载Battery Historian的源码：
  ``` bash
  C:\Users\xxx> go get -d -u github.com/google/battery-historian/...
  ```

- 下载完成之后，可以在$GOPATH\src\多了个github.com目录，这里面放的就是源码。接着，我们切换到源码目录对javascript文件进行编译：
``` bash
C:\Users\xxx> cd $GOPATH/src/github.com/google/battery-historian
# Compile Javascript files using the Closure compiler
C:\Users\xxx> go run setup.go
```
- 最后，就可以运行工具了（注意，运行工具时必须要切换到$GOPATH/src/github.com/google/battery-historian目录运行）：

``` bash
C:\Users\xxx> go run cmd/battery-historian/battery-historian.go --port 9998   // 这里不指定端口默认是9999
```

- 如果没报错，就可以在浏览器中访问http://127.0.0.1:9998，打开工具页面。

## 使用
在http://127.0.0.1:9998打开工具后，会让你选择数据文件，如下图所示：
  ![open_battery.png](/upload/image/zlw/open_battery.png)
- 我们看到这里有几个选项：
  >- 选择一个Bugreport文件，也就是通过adb bugreport > bugreport.txt获取的文件。
  >- Kernel Wakesource Trace。它是用来上传kernel 被唤醒的原因及事件的，具体怎么获得，这里不讲解了（我在ACE和eh880上面都没有试验成功）。参考github上面的介绍
  >- Power Monitor File。是用来记录耗电情况的。（其实这里我也没看明白是啥意思，按照文档说的，是执行adb bugreport > bugreport.txt来采集数据的）
  >- Switch to Bugreport Companson，意为可以对两个bugreport文件进行对比。（不过个人觉得对发现问题来说作用略微有点小，可以用来对测试结果进行容错校验）。
对于上面提到的几个选项，1、2、3可以同时选择，但是对于正常的测试，选择一个Bugreport就可以满足分析问题的需要了。

- 我们选择一个bugreport文件之后，界面整体分为上下两部分：上部分是将测试数据以图表的样式进行展示、下部分是详细的测试数据分类展示，如下图所示：
  ![top_battery.png](/upload/image/zlw/top_battery.png)   ![below_battery.png](/upload/image/zlw/below_battery.png)
- 上图中标出了对应部分都是做什么的，其中有几点需要说明：
  >- Userspace wakelock (all) 和 Userspace wakelock的区别：  Userspace wakelock 展示的是需要wakelock的第一个app进程，而Userspace wakelock (all) 展示的是所有的wakelock（需要执行adb shell dumpsys batterystats --enable full-wake-history命令）因此，我们查看Userspace wakelock (all) 就可以了。
  >- 在页面上有一些count/Hr、second/Hr等，表示的是1小时内的平均数据。（总数据/总时间）。
  >- 在页面中还提供了温度的变化，可以作为测试的辅助数据。

- 下面重点讲解下：正常测试发现问题的思路及对两份测试数据进行比较思路。

## 发现问题思路
- 以最近测试的新OA作为例子。

1）查看CPU运行状况
- cpu的运行直接造成耗电，我们关注下面两种问题：
  >- 连续一段时间，cpu一直在使用。（本例就是这样的）
  >- 使用cpu较频繁，间隔时间比较短。

- 如下图所示，是一个持续使用cpu的例子：
  ![cpu_battery.png](/upload/image/zlw/cpu_battery.png)

- 从图中我们看到有两处持续了20分钟在不停的使用CPU。

2）查看CPU对应的wakelock
- 还如上图所示，我们可以看到在CPU running的地方，同样也有Userspace wakelock (all)，我们将鼠标靠上去，可以看到如下图所示：
  ![wakelock_battery.png](/upload/image/zlw/wakelock_battery.png)

- 我们看到有一个xxx.xdja.xxx的wakelock持续占用了20分钟的cpu，这明显是有问题的。

3)电量消耗排行
- 如果上面通过cpu没有直接发现问题，我们可以查看系统所有app的电量消耗排行，如下图所示：
  ![电量排行.png](/upload/image/zlw/电量排行.png)

- 这里的例子还是新OA，这里可以看到排名前几位的都是系统的组件，这个也比较好理解，因为所有app对电量的消耗都是可以反映到各种系统组件上面的（比如：wifi、震动等）。这里还有个小经验：我们的app耗电一般是比系统（uid=1000）消耗是要少的，如果超过了系统的耗电，那么就需要引起注意了。   这里com.xdja.eoa的耗电超过了system。
- 另外，如果提交测试时提供了对比app，那么我们也要和对比app比较耗电情况。

4）单个app耗电查看
- 当发现我们测试的app比较耗电时。我们可以切换到app耗电详情来查看耗电原因：
  ![app_info_battery.png](/upload/image/zlw/app_info_battery.png)

- 如上图所示，衡量耗电量一般有三个方面：流量消耗、wakelock情况、进程工作情况：
  >- 流量消耗：一般我们要看流量消耗是否在合理范围内（发送数据包情况），主要是和竞品进行比较。经验数据：如果12个小时内，流量超过300KB（有可能不准确，有的app本身就是耗流量的，这里的300KB是心跳包的衡量大小。）有可能是请求频繁或者流量包较大等问题。
  >- wakelock情况：看每个wakelock总共的占用时间，如上图所示，这里Mqtt.xx.xdja.xxx共占用了40分钟时间，这明显有问题。（印证了上面cpu发现的问题）。
  >- 进程工作： 看各个进程占用cpu的时间，是否符合预期。经验数据：一般不会超过几分钟。

5）结合日志查看
- 如果通过上面的思路发现了问题，这时我们只是知道了大概的原因，想知道更详细的app到底做了什么，可以结合日志进行分析。
- 我们测试前，将所有的日志都保存下来（这个ACE有测试工具，可以将所有的日志保存下来）。
- 我们查看21:15:58-21:35:58这段时间的日志：
  ![申请.png](/upload/image/zlw/申请.png)     ![释放.png](/upload/image/zlw/释放.png)

- 对比两段日志，右边的是正常的，左边的是有问题的，我们发现左边的wakelock只有申请，但是没有被释放，这造成了这个wakelock持续了20分钟。

6）总结
- 这里仅仅是介绍了大体的思路，测试过程中的手段是多样的，而且是会随意结合起来使用的。这需要多测试积累经验。
- 大家可以先按照这个基本的思路来进行尝试。

## 两份测试数据对比
- 我个人觉得对比测试数据对发现app的耗电问题价值不是特别大，但是可以验证我们的测试数据是否稳定：举个例子，同样测试环境（同样的手机、相同时间、相同操作），但是获取到的bugreport数据却相差很大，那么这时候测试数据具有偶然性，所以我们还得再增加测试来确保数据的准确性。
- 我这里取得了两份测试数据，故意让他们测试环境不一样（测试时间不同），来看看有什么差异：
  ![对比.png](/upload/image/zlw/对比.png)

- 从上所示，左侧边栏我们比较熟悉，这里还是主要关注：Device's Power Estimates（耗电排行情况）和 Userspace Wakelocks（wakelock情况）
- 右侧我们看到有紫色的区域，这是工具为我们表示出可能存在问题的点。

### 总结
- 正常情况下，两份同等条件下获取的数据，差别不应该太大。

## 总结
- 本文主要是对battery-historian的基本使用做了个简单介绍。是方便大家在测试电量时能有分析问题的思路。
- 更多用法还得靠大家自己多测试去摸索。
