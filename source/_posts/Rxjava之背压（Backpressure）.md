---
title: Rxjava之背压（Backpressure）
date: 2017-04-06 23:08:20
tags: Backpressure, Rxjava
categories: Android开源库
---
## 概念
- Rxjava是一个观察者模式的架构，当这个架构中被观察者（Observable）和观察者（Observer）处于不同的线程环境中时，由于各自的工作量不一样，导致它们产生事件和处理事件的速度不一样，这就会出现两种情况：
>1. 被观察者产生事件慢一些，观察者处理事件很快。那么观察者就会等着被观察者发送事件，（好比观察者在等米下锅，程序等待，这没有问题）。
>2. 被观察者产生事件的速度很快，而观察者处理很慢。那就出问题了，如果不作处理的话，事件会堆积起来，最终挤爆你的内存，导致程序崩溃。（好比被观察者生产的大米没人吃，堆积最后就会烂掉）。

- 举一个会造成问题的例子：

``` file
//被观察者在主线程中，每1ms发送一个事件
Observable.interval(1, TimeUnit.MILLISECONDS)
//.subscribeOn(Schedulers.newThread())
//将观察者的工作放在新线程环境中

.observeOn(Schedulers.newThread())
//观察者处理每1000ms才处理一个事件
.subscribe(new Action1() {

@Override
public void call(Long aLong) {
try {
Thread.sleep(1000);
} catch (InterruptedException e) {
e.printStackTrace();
}
Log.w("TAG","---->"+aLong);
}
});
```
- 在上面的代码中，被观察者发送事件的速度是观察者处理速度的1000倍，运行代码后，抛出：

``` files
...
   Caused by: rx.exceptions.MissingBackpressureException
   ...
   ...
```
- 通过上面的例子，我们可以对背压（BackPressure）做一个明确的定义：在异步场景中，被观察者发送事件速度远快于观察者的处理速度的情况下，一种告诉上游的被观察者降低发送速度的策略。也就是说背压是流速控制的一种策略。
- 背压策略的一个前提是异步环境，也就是说，被观察者和观察者处于不同的线程环境中。
- 背压（Backpressure）并不是一个像flatMap一样可以在程序中直接使用的操作符，他只是一种控制事件流速的策略。

## 响应式拉取（reactive pull）
- 在RxJava的观察者模型中，被观察者是主动的推送数据给观察者，观察者是被动接收的。而响应式拉取则反过来，观察者主动从被观察者那里去拉取数据，而被观察者变成被动的等待通知再发送数据。示意图如下所示：
![rxjava_backpressure1.png](/upload/image/zlw/rxjava_backpressure1.png)

- 由上图可以看出，观察者可以根据自身实际情况按需拉取数据，而不是被动接收（也就相当于告诉上游观察者把速度慢下来），最终实现了上游被观察者发送事件的速度的控制，实现了背压的策略。
- 举个例子，代码如下：

``` java
//被观察者将产生100000个事件
Observable observable=Observable.range(1,100000);
class MySubscriber extends Subscriber<T> {
    @Override
    public void onStart() {
    //一定要在onStart中通知被观察者先发送一个事件
      request(1);
    }

    @Override
    public void onCompleted() {
        ...
    }

    @Override
    public void onError(Throwable e) {
        ...
    }

    @Override
    public void onNext(T n) {
        ...
        ...
        //处理完毕之后，在通知被观察者发送下一个事件
        request(1);
    }
}

observable.observeOn(Schedulers.newThread())
            .subscribe(MySubscriber);
```
- 从代码中我们知道，在onNext中，一定要所有的事务都处理完毕后，再调用request方法。
- 实际上，在上面的代码中，你也可以不需要调用request(n)方法去拉取数据，程序依然能完美运行，这是因为range --> observeOn,这一段中间过程本身就是响应式拉取数据，observeOn这个操作符内部有一个缓冲区，Android环境下长度是16，它会告诉range最多发送16个事件，充满缓冲区即可。不过话说回来，在观察者中使用request(n)这个方法可以使背压的策略表现得更加直观，更便于理解。
- 还需要说明一点，在最上面的例子，我们使用了interval操作符，但是这个例子中用了range操作符，是因为interval操作符是不支持背压的。

## Hot and Cold Observables
- Hot Observables 和cold Observables并不是严格的概念区分，它只是对于两类Observable形象的描述：
>1. Cold Observables：指的是那些在订阅之后才开始发送事件的Observable（每个Subscriber都能接收到完整的事件）。
>2. Hot Observables:指的是那些在创建了Observable之后，（不管是否订阅）就开始发送事件的Observable

- 我们一般使用的都是Cold Observable，除非特殊需求，才会使用Hot Observable,在这里，Hot Observable这一类是不支持背压的，而是Cold Observable这一类中也有一部分并不支持背压（比如interval，timer等操作符创建的Observable）。
- 都是Observable，结果有的支持背压，有的不支持，这就是RxJava1.X的一个问题。在2.0中，这种问题已经解决。
- 对于不支持背压的Observevable如何做流速控制呢，看下面的一些操作符。

## 流速控制相关的操作符
### 过滤（抛弃）
- 就是虽然生产者产生事件的速度很快，但是把大部分的事件都直接过滤（浪费）掉，从而间接的降低事件发送的速度。相关类似的操作符：Sample，ThrottleFirst....
- 以sample为例：

``` java
Observable.interval(1, TimeUnit.MILLISECONDS)

                .observeOn(Schedulers.newThread())
                //这个操作符简单理解就是每隔200ms发送里时间点最近那个事件，
                //其他的事件浪费掉
                  .sample(200,TimeUnit.MILLISECONDS)
                  .subscribe(new Action1() {
                      @Override
                      public void call(Long aLong) {
                          try {
                              Thread.sleep(200);
                          } catch (InterruptedException e) {
                              e.printStackTrace();
                          }
                          Log.w("TAG","---->"+aLong);
                      }
                  });
```
- 这是以杀敌一千，自损八百的方式解决这个问题，因为抛弃了绝大部分的事件，而在我们使用RxJava 时候，我们自己定义的Observable产生的事件可能都是我们需要的，一般来说不会抛弃，所以这种方案有它的缺陷。

### 缓存
- 就是虽然被观察者发送事件速度很快，观察者处理不过来，但是可以选择先缓存一部分，然后慢慢读。相关类似的操作符：buffer，window...  以buffer为例:

``` java
Observable.interval(1, TimeUnit.MILLISECONDS)

                .observeOn(Schedulers.newThread())
                //这个操作符简单理解就是把100毫秒内的事件打包成list发送
                .buffer(100,TimeUnit.MILLISECONDS)
                  .subscribe(new Action1>() {
                      @Override
                      public void call(List aLong) {
                          try {
                              Thread.sleep(1000);
                          } catch (InterruptedException e) {
                              e.printStackTrace();
                          }
                          Log.w("TAG","---->"+aLong.size());
                      }
                  });
```

### onBackpressurebuffer && onBackpressureDrop
- onBackpressurebuffer：把observable发送出来的事件做缓存，当request方法被调用的时候，给下层流发送一个item(如果给这个缓存区设置了大小，那么超过了这个大小就会抛出异常)。
- onBackpressureDrop：将observable发送的事件抛弃掉，直到subscriber再次调用request（n）方法的时候，就发送给它这之后的n个事件。
- 以onBackpressureDrop为例：

``` java
Observable.interval(1, TimeUnit.MILLISECONDS)
                .onBackpressureDrop()
                .observeOn(Schedulers.newThread())
               .subscribe(new Subscriber() {

                    @Override
                    public void onStart() {
                        Log.w("TAG","start");
//                        request(1);
                    }

                    @Override
                      public void onCompleted() {

                      }
                      @Override
                      public void onError(Throwable e) {
                            Log.e("ERROR",e.toString());
                      }

                      @Override
                      public void onNext(Long aLong) {
                          Log.w("TAG","---->"+aLong);
                          try {
                              Thread.sleep(100);
                          } catch (InterruptedException e) {
                              e.printStackTrace();
                          }
                      }
                  });
```
- 运行结果是：

``` file
onNext: 0
onNext: 1
onNext: 2
...
onNext: 15
onNext: 1608
onNext: 1609
...
onNext: 1623
```
- 由运行结果可以看出，前面 16 个数据正常的被处理的，这是应为 observeOn 在切换线程的时候， 使用了一个 16 个数据的小缓冲。
- 你可能会觉得这两个操作符和上面讲的过滤和缓存很类似，确实，功能上是有些类似，但是这两个操作符提供了更多的特性，那就是可以响应下游观察者的request(n)方法了，也就是说，使用了这两种操作符，可以让原本不支持背压的Observable“支持”背压了。
