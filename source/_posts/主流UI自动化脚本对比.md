---
title: 主流UI自动化脚本录制和执行工具对比
date: 2017-02-24 12:21:17
tags: UI自动化,脚本录制执行
categories: 测试工具
---
## 背景
- 最近和公司其他同学产生了一个想法，现在公司各个app，都需要有专门的人去写UI自动化脚本（appium），且推动不同业务的同学使用appium的过程比较缓慢，所以就想能不能搞个工具，在手机端操作，能自动生成对应的自动化脚本，这样可以大大提高工作效率且非常好推广。
- 工欲善其事，必先利其器，就花了一天的时间，把主流的脚本录制工具研究了一遍。

## 详细介绍
### Espresso
#### 简介
- 这玩意是AndroidStudio从2.2+版本自带的一个工具，中文名叫浓咖啡，意思就是你喝着咖啡，就把活做了，老外在起名字上非常形象啊。它从界面到交互使用起来还是比较方便的。

#### 使用方法
- 首先确保AndroidStudio的版本是2.2+。
- 确保你有app，能编译通过的源码。
- 菜单栏【Run】— 【Record Espresso Test】
- 接着就让你选择要执行脚本录制app的工程（如果你有多个model）以及哪个设备。
- 代码运行成功，会弹出一个【Record Your Test】的UI界面，这时候你只需要在手机端操作你的app就好了。如下图所示：
![Espresso](/upload/image/zlw/espresso.PNG)
- 当你的用例执行完成之后，点击【Complete Recording】，就会生成对应的java脚本文件（会让你起个名字）。
- 接下来要做的是对java脚本做些调整就好了。（两个操作间插入间隔时间、逻辑）

#### 原理分析
##### 录制
- Espresso录制时用到了BreakpointDescriptor、BreakpointCommand，它们是和调试程序相关的API。
- 说到这里，你大概能猜到了吧，Espresso在启动时会初始化很多BreakpointDescriptor，比如：performClick方法、onTextChanged方法等，换句话说它是在点击事件、EditText的文本变化打上了断点。
- 当用户在手机上操作app时，比如点击按钮时，会触发创建的断点，获取UI元素的详细信息。
- 当用户点击【Complete Recording】时，会将采集的内容，通过Apache的 Velocity 库，生成java文件。

##### 执行脚本
- Espresso的java脚本如下所示：
```java
        ViewInteraction button5 = onView(
                allOf(withId(R.id.prepare), withText("初始化接口"), isDisplayed()));
        button5.perform(click());

        ViewInteraction button6 = onView(
                allOf(withId(R.id.SecuritySDKManager), withText("SecuritySDKManager"), isDisplayed()));
        button6.perform(click());

        pressBack();

        ViewInteraction button7 = onView(
                allOf(withId(R.id.SecurityGroupManager), withText("SecurityGroupManager"), isDisplayed()));
        button7.perform(click());

        pressBack();
```
- 还是以点击按钮为例，跟踪代码进去perform方法，看它主要是调用了WindowManagerEventInjectionStrategy的injectKeyEvent。通过查看注释，这个方法是用来将KeyEvent事件注入到Android System。
- 我们再跟踪代码，发现其实也没啥高深的，它通过反射获取了WindowManager，通过它来注入事件，代码如下所示：
``` java
IBinder wmbinder = ServiceManager.getService( "window" );
IWindowManager m_WndManager = IWindowManager.Stub.asInterface( wmbinder );
// key down
m_WndManager.injectKeyEvent( new KeyEvent( KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A ),true );
// key up
m_WndManager.injectKeyEvent( new KeyEvent( KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A ),true );
```
- 不过这种方式，只针对自己的app，不能像其他app注入事件。
- 偷偷告诉你，monkey也是这么干的。。

#### 不足之处
- 插入时间间隔不方便，需要自己写个方法来实现。

### MonkeyTalk
#### 简介
- 从官网copy过来的介绍：MonkeyTalk是世界上最强大的移动应用测试工具。它实现iOS及Android上真实的、功能交互的自动化测试 ——包括从最简单的冒烟测试到复杂的数据驱动测试集的所有的测试。它支持原生、移动、混合型应用，在虚拟机或者真实设备上。
- MonkeyTalk分为社区版（免费）和 专业版（付费），不过可以找破解版的，我废了好大的劲找到（链接: http://pan.baidu.com/s/1dF1NagT 密码: 1ksq）

#### 使用方法
##### 手机端
- MonkeyTalk需要在打测试apk时，集成一个monkeytalk-agent-xxx.jar（具体用Eclipse或者AndroidStudio配置参考https://testerhome.com/topics/3082）
- 集成jar包后，打包成功即可。

##### PC端
- 初次使用，感觉这货特别像Eclipse，要先建一个project，注意创建project选择【MonkeyTalk project】。
- 创建工程的过程很简单，大家根据软件的提示一步步的操作就行。
- 创建好工程之后，可以点击菜单栏那个像火箭发射的按钮，启动测试app，然后点击【Record】按钮就可以开始录制了。
- 在手机端执行你的用例，生成的脚本如下图所示：
![MonkeyTalk](/upload/image/zlw/monkeytalk.PNG)
- 录制完成后，点击【stop】就自动保存了，然后点击【play all】就能开始执行脚本。
- 注意，点【play all】时，手机端app页面要切换到录制开始时的页面，否则会出现元素找不到的错误。

#### 原理分析
##### 录制
- MonkeyTalk，使用了aspectj AOP编程，在onCreate（页面切换）、setListener（点击事件）、setText（文本框输入文本）等创建了切面，换句话说当用户操作手机时，上面提到的monkeytalk-agent.jar中的切面会拦截到点击、输入文本之类的操作，然后获取界面元素。（aspectj的原理我没花太多事件研究，感兴趣的可以研究下）
- PC端开始录制时，会去创建RecordServer，它其实是去创建了一个serverSocket（端口是16861），用于监听手机端发送过来的数据，而上面提到手机端集成了一个agent的jar包，用于将采集到的UI元素及操作事件，通过这个serverSocket发送给PC。手机端的端口号是（16862）
- PC端收到采集的数据，做展示，生成mt脚本。

##### 执行脚本
- 执行脚本时同样，在手机端会有一个PlayBackServer，用于接受PC端发送过来要执行的脚本。
- 这个过程中有两个关键的数据：monkeyId（元素的标识id或者tag）和ComponentType（Button、TextView），然后手机端拿到PC端发送的这两个值之后，会在当前页面遍历所有的View，找到对应的界面元素，执行相对应的操作（xxxAutomator的play方法）。
- play方法是直接作用到Android View上的，比如滑动，最终会调用View.scrollTo()，这个也好理解，因为这个工具是直接dump到界面元素来进行操作的。

##### 其他
- 我只是介绍了基本的原理，大家可以下载对应的源码，更深入的了解它是怎么工作的。

#### 不足之处
- 生成的脚本维护性较差。
- 专业版软件要收费。

### MonkeyRunner
#### 简介
- MonkeyRunner是Google自带的一款工具，通过它可以截屏、安装包、发送一些keyEvent，可以通过写python脚本实现。
- MonkeyRunner也有录制脚本的功能，要通过两个脚本，record.py 和  playback.py，如下所示
``` Python
#recorder.py
#<br>__author__ = 'paul'
from com.android.monkeyrunner import MonkeyRunner as mr
from com.android.monkeyrunner.recorder import MonkeyRecorder as recorder

device = mr.waitForConnection()
recorder.start(device)
```
``` Python
#playback.py
#<br>__author__ = 'paul'
import sys
from com.android.monkeyrunner import MonkeyRunner

CMD_MAP = {
    "TOUCH": lambda dev, arg: dev.touch(**arg),
    "DRAG": lambda dev, arg: dev.drag(**arg),
    "PRESS": lambda dev, arg: dev.press(**arg),
    "TYPE":lambda dev, arg:dev.type(**arg),
    "WAIT":lambda dev, arg:MonkeyRunner.sleep(**arg)
}

#Process a single file for the specified device.
def process_file(fp,device):
    for line in fp:
        (cmd,rest) = line.split("|")
        try:
            #Parse the pydict
            rest = eval(rest)
        except:
            print "unable to parse options"
            continue

        if cmd not in CMD_MAP:
            print "unknown command: " + cmd
            continue

        CMD_MAP[cmd](device, rest)


def main():
    file = sys.argv[1]
    fp = open(file, "r")

    device = MonkeyRunner.waitForConnection()

    process_file(fp,device)
    fp.close();


if __name__ == "__main__":
    main()
```
#### 使用
##### 录制
- 直接在命令行执行monkeyrunner record.py 打开UI界面。
- 然后在PC端的UI界面操作，界面上有几个按钮：
>1.wait： 用来插入下一次操作的时间间隔，点击后即可设置时间，单位是秒
2.Press a Button：用来确定需要点击的按钮，包括menu、home、search，以及对按钮的press、down、up属性
3.Type Something：用来输入内容到输入框，选中某个输入框
4.Fling：用来进行拖动操作，可以向上、下、左、右，以及操作的范围
5.Export Actions：用来导出脚本
6.Refresh Display：用来刷新手机界面，有时候界面反映太慢，可以强制刷新一下。

##### 播放
- 直接在命令行执行monkeyrunner playback.py xxx.py  (生成的python脚本)
- 执行脚本的时候，最好将录制脚本的UI关掉，否则会有影响。

#### 原理
- moneyrunner主要用到了两个jar：ddmlib.jar和chimpchat.jar。
- 通过将PC端UI点击的坐标，根据比例转化成手机上的坐标，然后通过device执行。

#### 不足之处
- 界面用起来比较卡顿。
- 基于坐标来生成自动化脚本，兼容性会有问题且不容易维护。

### 其他工具
- 其他工具还有atx、Appium Inpsector，但是我都没有搞成功过。感兴趣的可以研究下。
- 脚本录制的思路很明确，通过坐标或者代码插桩获取控件信息及操作类型。
- 脚本回放的思路也很明确，将脚本转换成设备可以执行的命令，或者直接在控件上执行方法。
