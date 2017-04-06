---
title: Android对cpu的测试
date: 2016-10-31 14:49:52
tags: Android,cpu
categories: Android性能测试
---
## 背景知识
###### 先了解几个和Linux时间有关的名词：HZ、tick
- HZ:Linux内核每隔固定周期会发出时间中断（timer interrupt），HZ是用来定义每一秒有几次timer interrupts，比如：HZ为1000，就代表每秒有1000次timer interrupts。
- Tick：Tick是HZ的倒数，Tick = 1/HZ。即timer interrupt每发生一次中断的时间，比如：HZ为250，tick=4ms
- Android基于Linux，而Linux是一个典型的分时系统，CPU时间被分成多个时间片，这是多任务实现的基础，Linux内核依赖tick，即时钟中断来进行分时。


###### jiffies
- 有了以上预热知识，我们就能解释jiffies是什么了。jiffies是用来记录从开机开始，已经经过了多少个tick，每一次发生timer interrupt，jiffies就会增加1。
- 我们可以计算出来，其实内核每秒钟将jiffies变量增加HZ次。

###### /proc/stat
- 文件中存放的就是所有cpu的jiffies信息，如下图所示：
![battery_stat.png](/upload/image/zlw/battery_stat.png)
- 从上图可以看出我的手机是8核的，第一行代表总的cpu jiffies情况，后面cpu0到cpu1代表的是各个cpu的jiffies情况。
- user：从系统启动开始累计到当前时刻，用户态的jiffies，不包含nice值为负的进程。
- nice：从系统启动开始累计到当前时刻，nice值为负的进程所占用的jiffies。（nice值是Linux中用来设置线程进程优先级的，取值范围是-20到19，nice值越小，进程优先级越高，获得cpu调用的机会越多，在Android中一般用不到nice值，不过我们可以控制线程的优先级）
- system：从系统启动开始累计到当前时刻，系统态的jiffies。
- idle：从系统启动开始累计到当前时刻，除硬盘IO等待时间以外其它等待的jiffies。
- iowait ： 从系统启动开始累计到当前时刻，硬盘IO等待的jiffies。
- irq ： 从系统启动开始累计到当前时刻，硬中断的jiffies
- softirq ：从系统启动开始累计到当前时刻，软中断的jiffies。
- 上面这几项相加=一段时间内总的cpu时间片。
###### /proc/uid/stat
- 这个文件中存放的是uid进程的cpu 运行情况。如下图所示：
![uidstat.PNG](/upload/image/zlw/uidstat.PNG)
- 这里值比较多，我就不一一介绍了，都是关于进程的信息，有兴趣的同学可以参考：[/proc/[pid]/stat文件分析](http://blog.sina.com.cn/s/blog_aed19c1f0102wcuo.html)。
我们重点关注第14、15、16、17列的信息：
- 第14行是utime= 26，表示该任务在用户态运行的时间，单位为jiffies
- 第15行是stime = 15，表示该任务在核心态运行的时间，单位为jiffies
- 第16行是cutime = 0 ，累计的该任务的所有的waited-for进程（已死进程）曾经在用户态运行的时间，单位为jiffies
- 第17行是cstime = 0，累计的该任务的所有的waited-for进程（已死进程）曾经在核心态运行的时间，单位为jiffies
- 所以该进程占用cpu的总时间就是上面四个值相加：utime+stime+cutime+cstime。

## 计算CPU占有率
###### 计算Android整机的CPU占有率
- 读取/proc/stat文件的数据可以拿到总的cpu时间片。
- 数据采集时间间隔要足够短（考虑到数据精确性），我一般采用1s或者小于1s的时间。
- 假设前后取了两次总的cpu时间片分别为total1，total2。则这个时间段内总的cpu时间片 total= total2 - total1
- 假设前后取了两次空闲时间idle时间片（上面有提到，/proc/stat 第四列），分别为i1, i2。则这个时间段内idle的时间片 idle= i2 - i1。
- 总的cpu占用率 = 100*(total - idle） / total

###### 计算单个进程的cpu占用率
- 读取/proc/uid/stat 可以得到某一进程占用的cpu时间片。
- 数据采集时间间隔要足够短（考虑到数据精确性），我一般采用1s或者小于1s的时间。
- 假设前后取了两次进程占用cpu时间片的值，分别为：proc1， proc2 。 则这个时间段内进程占用的时间片为proc = proc2 - proc1
- 假设前后取了两次总的cpu时间片分别为total1，total2。则这个时间段内总的cpu时间片 total= total2 - total1
- 单个进程的cpu占有率  = 100*(total - proc) / total

###### top 和 dumpsys cpuinfo区别
- 网上有很多的帖子来介绍如何计算cpu使用率，其中介绍了必然会提到两种方式：top命令和dumpsys cpuinfo。我一直比较好奇他们之间有什么区别，并且他们的结果有些不同。
- 主要是因为他们的计算方法是不同的，top命令方式和dumpsys cpuinfo两者计算分子是相同的，不同的是分母：top命令的分母是上面提到的时间片jiffies，而dumpsys cpuinfo的分母是通过SystemClock.uptimeMillis()计算时间差。
- 不过通过top命令得到的cpu占有率是整数的。所以之前经常看到0%的，以为真的是0。其实有可能是0.x%。
- 网上有个哥们讲了这个问题，可以参考[top和dumpsys cpuinfo](http://blog.csdn.net/oujunli/article/details/51463707)，也可以自己查看下源码验证下这哥们讲的是否是正确的。

###### AndroidStudio cpu monitor测App cpu占用率不为0
- 在之前的公司，要求App静默时的cpu占有率达到0，但是用AndroidStudio 测试总是不为0，会维持在0.22%到0.44%。
- 做了个实验，用Python脚本采用上面的方法计算app的cpu占有率。得到的结果和AndroidStudio monitor的结果对比比较接近，都是0.22%-0.44%。
- 第一次尝试：怀疑是不是和采集数据时间短，所以我每隔2s采集一次数据，发现和原来的结果一样，所以这种情况排除。
- 第二次尝试：我发现用AndroidStudio monitor trace的结果中，只有jdwp一个线程在工作，于是我猜测是不是AndroidStudio在不断通过jdwp在干着什么工作。所以我关掉了AndroidStudio，然后再运行Python脚本，果然cpu占有率变成0。然后我又再次打开AndroidStudio，不选择调试【安全邮件】进程，用Python脚本采集cpu占有率数据，果然cpu占有率还是0。然后选择调试【安全邮件】进程，再用Python脚本采集数据，cpu占有率变成了0.22%-0.44%。
- 通过上面的实验也就验证了AndroidStudio cpu monitor 测试App cpu占有率不为0的原因：JDWP线程，它是在每个进程启动的时候都会开启的一个用于和DDMS进行通信的线程。
- ** 结论是建议用脚本[测试cpu占有率](http://gitlab.idc.safecenter.cn/zhouliwei/AutoScriptForAndroid/blob/master/commonoperation/cpu_inspect_thread.py)，或者用AndroidStudio测试时当cpu占有率大于0.44%时可能是有问题的。**

## 分析CPU相关问题
###### 测试场景
- 静默状态：静默状态cpu占有率应该为0。
- 操作之后停止操作：这种情况下，cpu占有率会在一定时间后变为0。
- 操作过程中：cpu占有率不应该非常高，超过50%（没有数据支撑，只是拍脑袋感觉）。
- 之前用AndroidStudio monitor测试，cpu静默时不能为0，维持在0.22%-0.44%，通过抓取MethodTracing，发现只有一个JDWP线程。

###### 分析问题
- 分析问题的手段有很多：TraceView、DDMS的Threads（查看哪些线程还活着）、AndroidStudio monitor抓trace。
- 我们结合着AndroidStudio monitor的trace来分析下，因为容易操作且采集的数据比较丰富。
- 因为我手边没有cpu问题的例子，所以我用一个操作过程中cpu变化的场景来分析下：打开【安全邮件】，然后在AndroidStudio cpu monitor点击【start Method Tracing】如下图所示：![method_tracing.PNG](/upload/image/zlw/method_tracing.PNG)
- 然后多次滑动邮件列表，持续大概10几秒，可以观察到cpu占有率在6%左右。
- 再次点击【start tracing method】（这个时候是stop tracing method），AndroidStudio会自动打开一份trace文件，我们主要分析这份trace文件，它包括了每个方法的耗时，对我们分析问题非常有帮助。
- 我们先来看下它长什么样子，如下图所示：![cpu_method_trace.PNG](/upload/image/zlw/cpu_method_trace.PNG)
- Thread 表示当前进程里面还有什么线程在工作，一般我们关心的是main（主线程）、xx_thread_pool、自己命名的线程等，对于Binder、JDWP这些是系统干活的线程，一般我们不需要关心。
- x-axis 表示的是下面这个柱状图横坐标以什么为基准排序，有两个选项：thread time是线程执行时间不包括线程 sleep时间；Wall Clock Time 是系统运行时间，包括线程sleep等时间。
- 搜索按钮  可以用来搜索我们感兴趣的内容，比如我们应用的包名。搜索结果在柱状图中会有体现。
- Invocation Count 表示方法执行了多少次。
- Inclusive Time 表示方法执行了多长时间（单位是微秒），包括执行其他方法的时间。
- Exclusive Time 表示方法本身执行了多长时间，不包括执行其他方法的时间。

- 回到上面的例子，我们在搜索框中搜索：xdja 如下图所示：![tracing_method.PNG](/upload/image/zlw/tracing_method.PNG)
- 可以看到有相关的方法在工作，如果是静默状态，理论上是不应该有任何方法在执行的（排除心跳包的场景），所以这个可能就是存在问题的。
- 如果通过上面的方法不能确定问题，我们可以进一步的通过TraceView来分析方法的调用栈，从而排查问题。

## 总结
- 测App 的CPU占有率，用脚本测要关掉AndroidStudio对App的调试，排除其对采集数据的影响。如果用AndroidStudio cpu monitor测，静默时CPU占有率稳定在0.22%-0.44%，并不为0。
- 首先用AndroidStudio cpu monitor的【start Method Tracing】来采集方法执行时间分析问题。分析思路是查找和自己应用相关的操作。
- 更进一步的分析问题可以使用TraceView抓取方法调用栈。
