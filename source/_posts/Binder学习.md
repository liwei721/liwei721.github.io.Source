---
title: Binder学习
date: 2017-04-21 11:59:00
tags: Binder，Android
categories: Android技术
---

## 背景
- Binder是Android系统中非常重要的特性之一，也就是我们常说的跨进程通信机制，它是系统间各个组件的桥梁。
- 在学习[Android Hook Binder](http://weishu.me/2016/02/16/understand-plugin-framework-binder-hook/)时，发现对Binder的理解还不深，之前其实研究过挺深的，但是时间长了都又还回去了，因此这里做个学习笔记，同时可以和大家交流。
- 本文参考[Binder学习](http://weishu.me/2016/01/12/binder-index-for-newer/)

### Linux基础知识
#### 进程隔离
- 进程隔离是为保护操作系统中进程互不干扰而设计的一组不同硬件和软件的技术。这个技术是为了避免进程A写入进程B的情况发生。 进程的隔离实现，使用了虚拟地址空间。进程A的虚拟地址和进程B的虚拟地址不同，这样就防止进程A将数据信息写入进程B。
- 操作系统的不同进程之间，数据不共享；对于每个进程来说，它都天真地以为自己独享了整个系统，完全不知道其他进程的存在；因此一个进程需要与另外一个进程通信，需要某种系统机制才能完成。

#### 用户空间/内核空间
- Linux Kernel是操作系统的核心，独立于普通的应用程序，可以访问受保护的内存空间，也有访问底层硬件设备的所有权限。
- 对于Kernel这么一个高安全级别的东西，显然是不容许其它的应用程序随便调用或访问的，所以需要对Kernel提供一定的保护机制，这个保护机制用来告诉那些应用程序，你只可以访问某些许可的资源，不许可的资源是拒绝被访问的，于是就把Kernel和上层的应用程序抽像的隔离开，分别称之为Kernel Space和User Space。

#### 系统调用/内核态/用户态
- 用户控件访问内核空间的唯一方式是系统调用，通过这个统一入口接口，所有的资源访问都是在内核的控制下执行，以免导致用户程序对系统资源的越权访问，保证安全。
- 当一个任务（进程）执行系统调用而陷入内核代码中执行时，我们称进程处于内核运行态（或简称内核态），同样的，当程序在执行用户自己的代码时，则其处于用户运行态（用户态）。

#### 内核模块/驱动
- 通过系统调用，用户空间可以访问内核空间，那么如果一个用户空间想与另外一个用户空间进行通信怎么办呢？很自然想到的是让操作系统内核添加支持；传统的Linux通信机制，比如Socket，管道等都是内核支持的；但是Binder并不是Linux内核的一部分，它是怎么做到访问内核空间的呢？Linux的动态可加载内核模块（Loadable Kernel Module，LKM）机制解决了这个问题；模块是具有独立功能的程序，它可以被单独编译，但不能独立运行。它在运行时被链接到内核作为内核的一部分在内核空间运行。这样，Android系统可以通过添加一个内核模块运行在内核空间，用户进程之间的通过这个模块作为桥梁，就可以完成通信了。
- Android系统中，这个运行在内核空间的，负责各个用户进程通过Binder通信的内核模块叫做 **Binder驱动**

## 为什么使用Binder？
- 性能和安全：Binder相对于传统的通信方式更加高效，另外Binder机制从协议本身就是支持对通信双方做身份校验的，因而大大提升了安全性，这个也是Android权限模型的基础。

## Binder通信模型
-对于跨进程通信的双方，姑且称之为一方是Server进程（简称server），Client进程（client） 由于进程隔离的存在，他们之间没办法通过简单的方式进行通信，那么Binder机制是怎么做的呢？   这里有一个非常形象的例子来形容Binder通信，我们日常生活中通信过程：假如A和B要进行通信，当前的媒介是打电话（假如A是Client，B是server， A给B打电话）：
>1. A 首先去通信录查询B的电话号码。
>2. A 拿到号码之后，通过手机拨号，连接到基站，（告诉基站我要和B通话）。
>3. 这时候基站就会帮A和B建立一条通信链路。

- 那么对应到Binder驱动中，Binder驱动相当于基站，而通信录则对应一个叫做ServiceManager的东西。通信模型如下图所示：

![binder-model.png](/upload/image/zlw/binder-model.png)

- 整个通信步骤如下：
>1. SM建立(建立通信录)；首先有一个进程向驱动提出申请为SM；驱动同意之后，SM进程负责管理Service（注意这里是Service而不是Server，因为如果通信过程反过来的话，那么原来的客户端Client也会成为服务端Server）不过这时候通信录还是空的，一个号码都没有。
>2. 各个Server向SM注册(完善通信录)；每个Server端进程启动之后，向SM报告，我是zhangsan, 要找我请返回0x1234(这个地址没有实际意义，类比)；其他Server进程依次如此；这样SM就建立了一张表，对应着各个Server的名字和地址；就好比B与A见面了，说存个我的号码吧，以后找我拨打10086；
>3. Client想要与Server通信，首先询问SM；请告诉我如何联系zhangsan，SM收到后给他一个号码0x1234；Client收到之后，开心滴用这个号码拨通了Server的电话，于是就开始通信了。

## Binder进程间通信原理
- 假如Client进程想要调用Server进程的object对象的一个方法add；如下图所示：
![2016binder-procedure.png](/upload/image/zlw/2016binder-procedure.png)

- 首先，Server进程要向SM注册；告诉自己是谁，自己有什么能力；在这个场景就是Server告诉SM，它叫zhangsan，它有一个object对象，可以执行add 操作；于是SM建立了一张表：zhangsan这个名字对应进程Server;
- 然后Client向SM查询：我需要联系一个名字叫做zhangsan的进程里面的object对象；这时候关键来了：进程之间通信的数据都会经过运行在内核空间里面的驱动，驱动在数据流过的时候做了一点手脚，它并不会给Client进程返回一个真正的object对象，而是返回一个看起来跟object一模一样的代理对象objectProxy，这个objectProxy也有一个add方法，但是这个add方法没有Server进程里面object对象的add方法那个能力；objectProxy的add只是一个傀儡，它唯一做的事情就是把参数包装然后交给驱动。
- 但是Client进程并不知道驱动返回给它的对象动过手脚，毕竟伪装的太像了，如假包换。Client开开心心地拿着objectProxy对象然后调用add方法；我们说过，这个add什么也不做，直接把参数做一些包装然后直接转发给Binder驱动。
- 驱动收到这个消息，发现是这个objectProxy；一查表就明白了：我之前用objectProxy替换了object发送给Client了，它真正应该要访问的是object对象的add方法；于是Binder驱动通知Server进程，调用你的object对象的add方法，然后把结果发给我，Sever进程收到这个消息，照做之后将结果返回驱动，驱动然后把结果返回给Client进程；于是整个过程就完成了。
- 由于驱动返回的objectProxy与Server进程里面原始的object是如此相似，给人感觉好像是直接把Server进程里面的对象object传递到了Client进程；因此，我们可以说Binder对象是可以进行跨进程传递的对象。
- 但事实上我们知道，Binder跨进程传输并不是真的把一个对象传输到了另外一个进程；传输过程好像是Binder跨进程穿越的时候，它在一个进程留下了一个真身，在另外一个进程幻化出一个影子（这个影子可以很多个）；Client进程的操作其实是对于影子的操作，影子利用Binder驱动最终让真身完成操作。
- 理解这一点非常重要；务必仔细体会。另外，Android系统实现这种机制使用的是代理模式, 对于Binder的访问，如果是在同一个进程（不需要跨进程），那么直接返回原始的Binder实体；如果在不同进程，那么就给他一个代理对象（影子）；我们在系统源码以及AIDL的生成代码里面可以看到很多这种实现。
- 另外我们为了简化整个流程，隐藏了SM这一部分驱动进行的操作；实际上，由于SM与Server通常不在一个进程，Server进程向SM注册的过程也是跨进程通信，驱动也会对这个过程进行暗箱操作：SM中存在的Server端的对象实际上也是代理对象，后面Client向SM查询的时候，驱动会给Client返回另外一个代理对象。Sever进程的本地对象仅有一个，其他进程所拥有的全部都是它的代理。
- 总结说就是：Client进程只不过是持有了Server端的代理；代理对象协助驱动完成了跨进程通信。

## Binder到底是什么？
- Binder的设计采用了面向对象的思想，在Binder通信模型的四个角色里面，他们的代表都是“Binder”，这样，对于Binder通信的使用者而言，Server里面的Binder和Client里面的Binder没有什么不同，一个Binder对象就代表了所有，它不用关心实现的细节，甚至不用关心驱动及SM的存在。
- 通常意义下，Binder指的是一种通信机制，我们说AIDL使用Binder进行通信，指的是Binder这种IPC机制。
- 对于Server进程来说，Binder指的是Binder本地对象。
- 对于Client来说，Binder指的是Binder代理对象，它只是Binder本地对象的一个远程代理，对这个Binder代理对象的操作，会通过驱动最终转发到Binder本地对象上去完成；对于一个拥有Binder对象的使用者而言，它无须关心这是一个Binder代理对象还是Binder本地对象；对于代理对象的操作和对本地对象的操作对它来说没有区别。
- 对于传输过程而言，Binder是可以进行跨进程传递的对象；Binder驱动会对具有跨进程传递能力的对象做特殊处理：自动完成代理对象和本地对象的转换。

### 驱动里面的Binder
- 我们现在知道，Server进程里面的Binder对象指的是Binder本地对象，Client里面的对象值得是Binder代理对象；在Binder对象进行跨进程传递的时候，Binder驱动会自动完成这两种类型的转换；因此Binder驱动必然保存了每一个跨越进程的Binder对象的相关信息；在驱动中，Binder本地对象的代表是一个叫做binder_node的数据结构，Binder代理对象是用binder_ref代表的；有的地方把Binder本地对象直接称作Binder实体，把Binder代理对象直接称作Binder引用（句柄），其实指的是Binder对象在驱动里面的表现形式。

### 深入理解java层的Binder
- IBinder/IInterface/Binder/BinderProxy/stub:
>1. IBinder是一个接口，它代表了一种跨进程传输的能力，只要实现了这个接口，就能将这个对象进行跨进程传递，这是驱动底层支持的，在跨进程数据流经驱动的时候，驱动会识别IBinder类型的数据，从而自动完成不同进程Binder本地对象以及Binder代理对象的转换。
>2. IInterface代表的是远程server对象具有的能力，具体来说就是aidl里面的接口。
>3. java层的Binder，代表的是Binder本地对象，BinderProxy是Binder类的一个内部类，它代表远程的Binder对象的本地代理，这两个类都继承自IBinder, 因而都具有跨进程传输的能力；实际上，在跨越进程的时候，Binder驱动会自动完成这两个对象的转换。
>4. 在使用AIDL的时候，编译工具会给我们生成一个Stub的静态内部类；这个类继承了Binder, 说明它是一个Binder本地对象，它实现了IInterface接口，表明它具有远程Server承诺给Client的能力；Stub是一个抽象类，具体的IInterface的相关实现需要我们手动完成，这里使用了策略模式。

## AIDL过程分析
- 我们通过一个AIDL的使用，分析一下整个通信过程中，各个角色做了什么，AIDL到底是如何完成通信的。
- 首先定一个aidl接口

``` java
// ICompute.aidl
package com.example.test.app;
interface ICompute {
     int add(int a, int b);
}
```

- 编译之后，可以得到对应的ICompute.java类：

``` java
package com.example.test.app;

public interface ICompute extends android.os.IInterface {
    /**
     * Local-side IPC implementation stub class.
     */
    public static abstract class Stub extends android.os.Binder implements com.example.test.app.ICompute {
        private static final java.lang.String DESCRIPTOR = "com.example.test.app.ICompute";

        /**
         * Construct the stub at attach it to the interface.
         */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an com.example.test.app.ICompute interface,
         * generating a proxy if needed.
         */
        public static com.example.test.app.ICompute asInterface(android.os.IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof com.example.test.app.ICompute))) {
                return ((com.example.test.app.ICompute) iin);
            }
            return new com.example.test.app.ICompute.Stub.Proxy(obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_add: {
                    data.enforceInterface(DESCRIPTOR);
                    int _arg0;
                    _arg0 = data.readInt();
                    int _arg1;
                    _arg1 = data.readInt();
                    int _result = this.add(_arg0, _arg1);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements com.example.test.app.ICompute {
            private android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                mRemote = remote;
            }

            @Override
            public android.os.IBinder asBinder() {
                return mRemote;
            }

            public java.lang.String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            /**
             * Demonstrates some basic types that you can use as parameters
             * and return values in AIDL.
             */
            @Override
            public int add(int a, int b) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(a);
                    _data.writeInt(b);
                    mRemote.transact(Stub.TRANSACTION_add, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
        }

        static final int TRANSACTION_add = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    }

    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    public int add(int a, int b) throws android.os.RemoteException;
}
```
- 现在我们只需要继承ICompute.Stub这个抽象类，实现它的方法，然后在service的onBind方法里面返回就实现了AIDL。
- Stub类继承了Binder，表示它是一个Binder本地对象，然后实现了ICompute接口，ICompute本身是一个IInterface，因此它携带某种客户端需要的能力（这里是方法add）。
- 类内部还有一个Proxy，也就是Binder代理对象。
- 再看asInterface方法，我们bind一个Service之后，在onServiceConnecttion的回调中，就是通过这个方法得到一个远程的service。

``` java
/**
 * Cast an IBinder object into an com.example.test.app.ICompute interface,
 * generating a proxy if needed.
 */
public static com.example.test.app.ICompute asInterface(android.os.IBinder obj) {
    if ((obj == null)) {
        return null;
    }
    android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
    if (((iin != null) && (iin instanceof com.example.test.app.ICompute))) {
        return ((com.example.test.app.ICompute) iin);
    }
    return new com.example.test.app.ICompute.Stub.Proxy(obj);
}
```
- 函数的参数是IBinder类型的obj，这个对象是驱动给我们的（onServiceConnecttion的参数），如果是Binder本地对象，那么它就是Binder类型，如果是Binder代理对象，那就是BinderProxy类型，然后，它会查找本地对象，如果找到，说明Client和Server都在同一个进程，这个参数直接就是本地对象，直接强制转换然后返回，如果找不到，说明是远程对象（处于另一个进程）那么就需要创建一个Binder代理对象，让Binder代理实现对于远程对象的访问，如果是与一个远程Service对象进行通信，那么这里返回的一定是一个Binder代理对象，这个IBinder参数的实际上是BinderProxy。
- aidl中的add方法，在Stub类里面，add是一个抽象方法，我们需要继承这个类并实现它，如果Client和Server在同一个进程，那么直接调用这个方法，如果不在一个进程，就远程调用，远程调用是通过Binder代理完成的，在这个例子中就是Proxy，Proxy对于add方法的实现如下：

``` java
verride
public int add(int a, int b) throws android.os.RemoteException {
    android.os.Parcel _data = android.os.Parcel.obtain();
    android.os.Parcel _reply = android.os.Parcel.obtain();
    int _result;
    try {
        _data.writeInterfaceToken(DESCRIPTOR);
        _data.writeInt(a);
        _data.writeInt(b);
        mRemote.transact(Stub.TRANSACTION_add, _data, _reply, 0);
        _reply.readException();
        _result = _reply.readInt();
    } finally {
        _reply.recycle();
        _data.recycle();
    }
    return _result;
}
```
- 它首先用Parcel把数据序列化了，然后调用了transact方法，前面提到过，如果是Binder代理那么说明驱动返回的IBinder实际是BinderProxy，因此我们的Proxy类里面的mRomete实际类型应该是BinderProxy,我们看看BinderProxy的transact方法：

``` java
public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
       Binder.checkParcel(this, code, data, "Unreasonably large binder buffer");
       return transactNative(code, data, reply, flags);
   }
```
- transactNative()最终调用到native层，它最终调用到了talkWithDriver函数，看名字就知道，通信过程交给驱动完成，这个函数最后通过ioctl系统调用，Client进程陷入内核态，Client调用add方法的线程挂机等待返回，驱动完成一系列的操作之后唤醒Server进程，调用Server进程本地对象的onTransact函数（实际上由Server端线程池完成）。
- 再看Binder本地对象的onTransact方法（这里就是Stub类里面的此方法）：

``` java
@Override
public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
    switch (code) {
        case INTERFACE_TRANSACTION: {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        case TRANSACTION_add: {
            data.enforceInterface(DESCRIPTOR);
            int _arg0;
            _arg0 = data.readInt();
            int _arg1;
            _arg1 = data.readInt();
            int _result = this.add(_arg0, _arg1);
            reply.writeNoException();
            reply.writeInt(_result);
            return true;
        }
    }
    return super.onTransact(code, data, reply, flags);
}

```

- 在Server进程里面，onTransact根据调用号（每个AIDL函数都有一个编号，在跨进程的时候，不会传递函数，而是传递编号指明调用哪个函数）调用相关函数；在这个例子里面，调用了Binder本地对象的add方法；这个方法将结果返回给驱动，驱动唤醒挂起的Client进程里面的线程并将结果返回。于是一次跨进程调用就完成了。
- 至此，你应该对AIDL这种通信方式里面的各个类以及各个角色有了一定的了解；它总是那么一种固定的模式：一个需要跨进程传递的对象一定继承自IBinder，如果是Binder本地对象，那么一定继承Binder实现IInterface，如果是代理对象，那么就实现了IInterface并持有了IBinder引用；

## 结尾
- Proxy与Stub不一样，虽然他们都既是Binder又是IInterface，不同的是Stub采用的是继承（is 关系），Proxy采用的是组合（has 关系）。他们均实现了所有的IInterface函数，不同的是，Stub又使用策略模式调用的是虚函数（待子类实现），而Proxy则使用组合模式。为什么Stub采用继承而Proxy采用组合？事实上，Stub本身is一个IBinder（Binder），它本身就是一个能跨越进程边界传输的对象，所以它得继承IBinder实现transact这个函数从而得到跨越进程的能力（这个能力由驱动赋予）。Proxy类使用组合，是因为他不关心自己是什么，它也不需要跨越进程传输，它只需要拥有这个能力即可，要拥有这个能力，只需要保留一个对IBinder的引用。
- 再去翻阅系统的ActivityManagerServer的源码，就知道哪一个类是什么角色了：IActivityManager是一个IInterface，它代表远程Service具有什么能力，ActivityManagerNative指的是Binder本地对象（类似AIDL工具生成的Stub类），这个类是抽象类，它的实现是ActivityManagerService；因此对于AMS的最终操作都会进入ActivityManagerService这个真正实现；同时如果仔细观察，ActivityManagerNative.java里面有一个非公开类ActivityManagerProxy, 它代表的就是Binder代理对象；是不是跟AIDL模型一模一样呢？那么ActivityManager是什么？他不过是一个管理类而已，可以看到真正的操作都是转发给ActivityManagerNative进而交给他的实现ActivityManagerService 完成的。
