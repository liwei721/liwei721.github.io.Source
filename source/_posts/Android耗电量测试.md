---
title: Android耗电量测试
date: 2016-10-24 09:48:58
tags: Android耗电量
categories: Android性能测试
---
## 背景
- 这一周都在花时间在做电量方面的分析工作，主要是为了验证通过软件的方式测电量是否可以替代功耗仪测电量。

#### 功耗仪 or 软件方式
  至于为啥想用软件方式替代功耗仪，我个人的观点是：
###### 功耗仪
  - 功耗仪虽然比较精确，但是测的是整机的电量情况，对测App的电量会造成一定的影响，且不知道具体进行了什么操作。
  - 功耗仪操作比较麻烦，需要拆机，且不好进行兼容性操作。
  - 但功耗仪的优点是精确，任何操作或者变化都会引起电量的变化。

###### 软件方式
  - android从最开始都有统计电量的api，只是一直没有开放（可能是考虑到这个模块还不稳定），从android2.3到android7.0，api获取电流的粒度更细致，接口也发生了一些变化，不过整体思想是没有变化的。
  - api测电量的思想：Android中各个部件的耗电量 = W * t (W表示单位时间耗电量，这个值每个设备不一样，通过power_profile.xml文件记录，这个文件放在 /system/framework/framework-res.apk中。 t 表示这个部件运行了多长时间)。  APP的耗电量就是App在运行过程中，涉及到的各个部件消耗电量的总和。
  - 在Android4.4之前可以通过反射或者通过[某些手段](https://github.com/liwei721/android-hidden-api)访问隐藏API及internal的方式来获取电量数据.
  - 不过从android4.4开始，Android强制加了权限限制，如下图所示：
  ![batterystats](/upload/image/batterystats.png)
  - 所以从Android4.4开始，想通过api来获取电量也不是那么容易，好在从Android5.0开始，google 又开放了一个利器【batteryStats】（其实它就是执行的api代码），通过这个命令可以收集 从上次充电开始一段时间内的电量数据（换句话说就是不充电状态下的电量数据）。

## 软件方式实现原理
#### API方式
- 主要涉及到三个类和一个方法，分别是：BatteryStatsHelper、BatteryStatsImpl、PowerProfile，分别介绍下它们：

###### BatteryStatsImpl
- BatteryStatsImpl 其实是记录了Android各个部件的耗时及操作。Android对它的注释就是：All information we are collecting about things that can happen that impact battery life
- 它是通过BatteryStatsService获取的：

  ```java
  private static BatteryStatsImpl getStats(IBatteryStats service) {
          try {
              ParcelFileDescriptor pfd = service.getStatisticsStream();
              if (pfd != null) {
                  FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                  try {
                      byte[] data = readFully(fis, MemoryFile.getSize(pfd.getFileDescriptor()));
                      Parcel parcel = Parcel.obtain();
                      parcel.unmarshall(data, 0, data.length);
                      parcel.setDataPosition(0);
                      BatteryStatsImpl stats = com.android.internal.os.BatteryStatsImpl.CREATOR
                              .createFromParcel(parcel);
                      stats.distributeWorkLocked(BatteryStats.STATS_SINCE_CHARGED);
                      return stats;
                  } catch (IOException e) {
                      Log.w(TAG, "Unable to read statistics stream", e);
                  }
              }
          } catch (RemoteException e) {
              Log.w(TAG, "RemoteException:", e);
          }
          return new BatteryStatsImpl();
      }
  ```
- 如上所示，通过service.getStatisticsStream来获取 batterstats的数据（可以进一步查看BatteryStatsService源码）。

###### PowerProfile
- 它其实就干了一件事情，将上面提到的power_profile.xml加载解析到内存中，然后封装了一些比较常用的方法，比如获取cpu的频率级别个数等。
- 这里说下这个power_profile.xml中记录的数据，我们可以通过apktool工具反编译framework-res.apk，然后在/res/xml/目录下就能找到power_profile.xml。我们看下它里面内容大致是什么：

  ```xml
   <item name="none">0</item>
   <item name="screen.on">170</item>
   <item name="screen.full">440</item>
   <item name="bluetooth.active">30</item>
   <item name="bluetooth.on">3</item>
   <item name="wifi.on">10</item>
   <item name="wifi.active">50</item>
   <item name="wifi.scan">100</item>
   <item name="dsp.audio">25</item>
   <item name="dsp.video">180</item>
   <item name="gps.on">70</item>
   <item name="radio.active">350</item>
   <item name="radio.scanning">10</item>
  ```
- 其实这里面记录的正是各个Android部件的单位时间耗电量（类似于买东西时的价格表）。

###### BatteryStatsHelper 中processAppusage方法
- BatteryStatsHelper 主要作用是用来计算耗电量的，通过refreshStats方法来开始计算。
- 其中processAPPUsage是用来计算App耗电的（比如：wakelock、cpu），processMiscUsage是用来计算硬件耗电的（比如：wifi、屏幕）。
- 我们测App的耗电量，其实关注processAPPUsage就够了，这里就不贴代码了，可以自行去查看源码，逻辑还是挺简单的，只不过需要熟悉计算的方法，网上有很多介绍这段源码的，也可以Google了解。

###### 尝试的过程
- 第一次尝试 想通过反射去实现processAPPUsage的逻辑，从而能分别拿到每个部件的耗电是多少。但是尝试了一段时间，发现对于实现方法内部的逻辑，反射过于麻烦了，要反射的方法及对象太多。
- 第二次尝试用android-hidden-api去实现，这个方法其实就是替代反射，对于hiden的类、方法以及internal包下的类，Android在编译成sdk时，是过滤掉的，所以我们没办法直接使用他们，而android-hidden-api的思路是用设备中的android.jar替换本地sdk中的android.jar，然后就可以访问隐藏及internal包下的类。
- 尝试了很长时间后，发现我没办法拿到BatteryStatsImpl的对象，前面提到过，它是在BatteryStatsService中被初始化的，我们是没办法直接操作BatterStatsService的。因此这里得出个结论 **以后做任何尝试之前，需要搞清楚代码的原理，否则会多走很多弯路，必然会花费很多时间**
- 第三次尝试是从Android setting源码入手，参考它初始化BatteryStatsHelper及获取BatteryStatsImpl的方式。然后感觉快要出结果时，又碰到了一个crash，提示我没有BatteryStats的权限。上面也有提到过从android 4.4开始，强制增加了权限校验，非得是System才能有权限（App放到/system/app/中）。
- 第四次尝试是准备用hook的方式绕开权限控制，因为我发现他们都调用了一个统一的方法，且可以hook BatteryStatsService这个系统服务：

  ```java
  public void enforceCallingPermission() {
          if (Binder.getCallingPid() == Process.myPid()) {
              return;
          }
          mContext.enforcePermission(android.Manifest.permission.UPDATE_DEVICE_STATS,
                  Binder.getCallingPid(), Binder.getCallingUid(), null);
      }
  ```
- 想Hook掉这方法，让他啥也不干。这样就能绕过权限限制，完成之后运行发现还是报权限问题，于是查看源码，发现真是悲剧，发现有的方法调用的是：

  ```java
  mContext.enforceCallingPermission(
                  android.Manifest.permission.BATTERY_STATS, null);
  ```
这样hook这个方法就达不到目的了，只能另想办法。

- 第五个方案没有尝试，因为感觉前面投入时间太多了，其实我想绕过Android api，用它的思路自己计算或许可行，具体操作是：先读取power_profie.xml的信息，然后通过dumpsys 各个服务，得出各个部件运行的时间，然后计算部件的耗电量。

#### adb shell dumpsys batterystats
- 从Android5.0开始batterystats工具也能采集很详细的数据，所以最后我采用这种方式来获取App的耗电量，它能够采集：一段时间内，App使用了多久Cpu、WakeLock耗用多长时间及哪些进程操作了wakelock多长时间等。

###### 实现方式
- 连上手机，执行如下命令：

  ```bash
  $ adb shell dumpsys batterystats --enable full-wake-history
  $ adb shell dumpsys batterystats --reset 清空电量数据
  ```
- 拔掉手机，测试自己的场景。
- 连上手机，执行命令：

  ```bash
  $ adb shell batterystats package_name > bugreport.txt
    用于采集数据
  ```
- 我这里写了个Python脚本，主要是用来分析收集采集的数据，写入excel,可以在[公司内网](http://gitlab.idc.safecenter.cn/zhouliwei/AutoScriptForAndroid/blob/master/apptestcase/battery_auto_inspect.py)访问代码。
- 也可以用github上面的[battery-historian](https://github.com/liwei721/battery-historian)将结果图形化。不过生成的html，js文件需要翻墙才能访问。

###### 采集到的数据名词
- Estimated power use (mAh) ：它下面列出的是各个uid的耗电量，也就是app中各个应用和硬件的耗电。
- Computed drain ：是根据power_proile.xml计算出来的值。
- actual drain ：根据电池实际的电量消耗计算最小值到最大值的范围。

  ```java
  pw.print(prefix); pw.print("    Capacity: ");
                      printmAh(pw, helper.getPowerProfile().getBatteryCapacity());
                      pw.print(", Computed drain: "); printmAh(pw, helper.getComputedPower());
                      pw.print(", actual drain: "); printmAh(pw, helper.getMinDrainedPower());
                      if (helper.getMinDrainedPower() != helper.getMaxDrainedPower()) {
                          pw.print("-"); printmAh(pw, helper.getMaxDrainedPower());
                      }
  ```
- Statistics since last charge: 从上次充电之后的数据情况。

## 总结
- 拿了之前安全芯片的功耗仪测试数据，用软件的方式测，拿到的结果和功耗仪的数据还是有差别的，Android自己也承认了，用power_profile.xml计算出来的电量是模拟值。不过在结果中的actual drain值是个范围，感觉和功耗仪的结果比较接近。
- 能不能替代功耗仪测试电量，还得继续多个版本来看，从目前的测试情况来看，我感觉是可以替代看看的。因为软件方式可以看到这段时间内的耗电进程及拿到粗略的电量值。
