---
title: Rxjava2.0介绍
date: 2017-04-06 23:09:33
tags: Rxjava2
categories: Android开源库
---

## 背景
- Rxjava2.0相对Rxjava1.0有了很多的更新，因此需要花时间好好研究下。
- 文章的讲述顺序：观察者模式、操作符、线程调度。
- 有人用水管来表示观察者和被观察者，我觉得也挺形象的：
![rxjava2_stream.PNG](/upload/image/zlw/rxjava2_stream.PNG)
- 如上图所示，上游就是我们的被观察者Observable，下游就是观察者Observer


## 观察者模式
- Rxjava以观察者模式为骨架，在2.0中依然如此。不过此次更新，出现了两种观察者模式：
>1. Observable(被观察者)/Observer（观察者）
>2. Flowable(被观察者)/Subscriber(观察者)

![rxjava2-observeable.png](/upload/image/zlw/rxjava2-observeable.png)

- RxJava2.X中，Observeable用于订阅Observer，是不支持背压的，而Flowable用于订阅Subscriber，是支持背压(Backpressure)的。
- 在1.0中，关于背压最大的遗憾，就是集中在Observable这个类中，导致有的Observable支持背压，有的不支持。为了解决这种缺憾，新版本把支持背压和不支持背压的Observable区分开来。

### Observable/Observer
- Observable的正常用法如下：

``` java
Observable mObservable=Observable.create(new ObservableOnSubscribe<Integer>() {
           @Override
           public void subscribe(ObservableEmitter<Integer> e) throws Exception {
               e.onNext(1);
               e.onNext(2);
               e.onComplete();
           }
       });

Observer mObserver=new Observer<Integer>() {
           //这是新加入的方法，在订阅后发送数据之前，
           //回首先调用这个方法，而Disposable可用于取消订阅
           @Override
           public void onSubscribe(Disposable d) {

           }

           @Override
           public void onNext(Integer value) {

           }

           @Override
           public void onError(Throwable e) {

           }

           @Override
           public void onComplete() {

           }
       };

mObservable.subscribe(mObserver);
```
- 这里出现一个新类：ObservableEmitter，Emitter是发射器的意思，那这个类的作用就是发出事件。它有三种类型的事件：onNext(T value)、onComplete()、onError(Throwable error)，不过发送事件有一些规则要遵守：
>1. 上游可以发送无限个onNext, 下游也可以接收无限个onNext.
>2. 当上游发送了一个onComplete后, 上游onComplete之后的事件将会继续发送, 而下游收到onComplete事件之后将不再继续接收事件.
>3. 当上游发送了一个onError后, 上游onError之后的事件将继续发送, 而下游收到onError事件之后将不再继续接收事件.
>4. 上游可以不发送onComplete或onError.
>5. 最为关键的是onComplete和onError必须唯一并且互斥, 即不能发多个onComplete, 也不能发多个onError, 也不能先发一个onComplete, 然后再发一个onError, 反之亦然
>6. 关于onComplete和onError唯一并且互斥这一点, 是需要自行在代码中进行控制, 如果你的代码逻辑中违背了这个规则, 并不一定会导致程序崩溃. 比如发送多个onComplete是可以正常运行的, 依然是收到第一个onComplete就不再接收了, 但若是发送多个onError, 则收到第二个onError事件会导致程序会崩溃.

- 还有一个新类是Disposable，从字面意思“被抛弃的”，我们应该能猜到，它用于中断观察者和被观察者之间的联系，即取消订阅。不过需要注意的是：调用dispose()并不会导致上游不再继续发送事件, 上游会继续发送剩余的事件。Disposable的用处不止这些, 后面讲解到了线程的调度之后, 我们会发现它的重要性. 随着后续深入的讲解, 我们会在更多的地方发现它的身影。
- 还有一个变化时subscribe()，有多个重载的方法：
``` java
   public final Disposable subscribe() {}   
   public final Disposable subscribe(Consumer<? super T> onNext) {}    
   public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError) {}
   public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError, Action onComplete) {}    
   public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError, Action onComplete, Consumer<? super Disposable> onSubscribe) {}    
   public final void subscribe(Observer<? super T> observer) {}
```
- 最后一个带有Observer参数的我们已经使用过了,这里对其他几个方法进行说明:
>1. 不带任何参数的subscribe() 表示下游不关心任何事件,你上游尽管发你的数据去吧, 老子可不管你发什么.
>2. 带有一个Consumer参数的方法表示下游只关心onNext事件, 其他的事件我假装没看见。

- 因为Observable/Observer不支持背压，所以使用它们的时候，我们需要考虑的是，数据量是不是很大(官方给出以1000个事件为分界线，仅供各位参考)

### Flowable/Subscriber
- Flowable 的使用方法如下：

``` java
Flowable.range(0,10)
     .subscribe(new Subscriber<Integer>() {
         Subscription sub;
         //当订阅后，会首先调用这个方法，其实就相当于onStart()，
         //传入的Subscription s参数可以用于请求数据或者取消订阅
         @Override
         public void onSubscribe(Subscription s) {
             Log.w("TAG","onsubscribe start");
             sub=s;
             sub.request(1);
             Log.w("TAG","onsubscribe end");
         }

         @Override
         public void onNext(Integer o) {
             Log.w("TAG","onNext--->"+o);
             sub.request(1);
         }
         @Override
         public void onError(Throwable t) {
             t.printStackTrace();
         }
         @Override
         public void onComplete() {
             Log.w("TAG","onComplete");
         }
     });
```
- Flowable是支持背压的，也就是说，一般而言，上游的被观察者会响应下游观察者的数据请求，下游调用request(n)来告诉上游发送多少个数据。这样避免了大量数据堆积在调用链上，使内存一直处于较低水平。
- 从代码运行结果我们看出，当我们调用subscription.request(n)方法的时候，不等onSubscribe()中后面的代码执行，就会立刻执行到onNext方法，因此，如果你在onNext方法中使用到需要初始化的类时，应当尽量在subscription.request(n)这个方法调用之前做好初始化的工作;
- Flowable也可以通过creat()来创建：

``` java
Flowable.create(new FlowableOnSubscribe<Integer>() {
           @Override
           public void subscribe(FlowableEmitter<Integer> e) throws Exception {
               e.onNext(1);
               e.onNext(2);
               e.onNext(3);
               e.onNext(4);
               e.onComplete();
           }
       }
       //需要指定背压策略
       , BackpressureStrategy.BUFFER);
```
- Flowable虽然可以通过create()来创建，但是你必须指定背压的策略，以保证你创建的Flowable是支持背压的。
- 不同于上面的方式，当用Flowable.create创建Flowable时，即使调用了subscription.request(n)方法，也会等onSubscribe（）方法中后面的代码都执行完之后，才开始调用onNext。
- 尽可能确保在request（）之前已经完成了所有的初始化工作，否则就有空指针的风险。

### 其他观察者模式
- 除了上面两种观察者，还有一类观察者：
>1. Single/SingleObserver
>2. Completable/CompletableObserver
>3. Maybe/MaybeObserver

- 其实这三者都差不多，Maybe/MaybeObserver可以说是前两者的复合体，因此以Maybe/MaybeObserver为例简单介绍一下这种观察者模式的用法

``` java
//判断是否登陆
Maybe.just(isLogin())
    //可能涉及到IO操作，放在子线程
    .subscribeOn(Schedulers.newThread())
    //取回结果传到主线程
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(new MaybeObserver<Boolean>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onSuccess(Boolean value) {
                if(value){
                    ...
                }else{
                    ...
                }
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
```
- 上面就是Maybe/MaybeObserver的普通用法，你可以看到，实际上，这种观察者模式并不用于发送大量数据，而是发送单个数据，也就是说，当你只想要某个事件的结果（true or false)的时候，你可以用这种观察者模式。
- 下面是上面提到的所有观察者的接口：
``` java
//Observable接口
interface ObservableSource<T> {
    void subscribe(Observer<? super T> observer);
}
//Single接口
interface SingleSource<T> {
    void subscribe(SingleObserver<? super T> observer);
}
//Completable接口
interface CompletableSource {
    void subscribe(CompletableObserver observer);
}
//Maybe接口
interface MaybeSource<T> {
    void subscribe(MaybeObserver<? super T> observer);
}
//Flowable接口
public interface Publisher<T> {
    public void subscribe(Subscriber<? super T> s);
}
```
- 其实我们可以看到，每一种观察者都继承自各自的接口，这也就把他们能完全的区分开，各自独立（特别是Observable和Flowable）,保证了他们各自的拓展或者配套的操作符不会相互影响。这也是框架设计者的用意。

### 其他更新
- 从2.x开始，被观察者不再接收null作为数据源。

- 在这个图中，黄色水管表示子线程，深蓝色水管表示主线程。

## 操作符相关
- 操作符本质上变动不大，多是包名或者类的变动。

### Action
- 改动如下：

``` file
Rx1.0-----------Rx2.0

Action1--------Consumer
Action2--------BiConsumer
ActionN--------Custom<Object[]>
```

### Function
- 同上也是命名方式的改变：

``` file
Rx1.0-----------Rx2.0

Func1--------Function
Func2--------BiFunction
Func3--------Function3
........
func9--------Function9
FuncN--------Function<Object[],R>
```
### 变换操作符
#### map
- 在背景中，我们引进了一张图片用水管来比喻观察者和被观察者，这里我们也可以引进一张图片
![rxjava2_map.PNG](/upload/image/zlw/rxjava2_map.PNG)

- 图中map函数作用是将圆形事件转成矩形事件，从而导致下游的事件都变成了矩形事件。代码例子：

``` java
Observable.create(new ObservableOnSubscribe<Integer>() {            
           @Override
           public void subscribe(ObservableEmitter<Integer> emitter) throws Exception {
               emitter.onNext(1);
               emitter.onNext(2);
               emitter.onNext(3);
           }
       }).map(new Function<Integer, String>() {            
           @Override
           public String apply(Integer integer) throws Exception {                
               return "This is result " + integer;
           }
       }).subscribe(new Consumer<String>() {           
           @Override
           public void accept(String s) throws Exception {
               Log.d(TAG, s);
           }
       });
```

#### flatmap
- flatmap是一个非常强大的操作符，它将一个发送事件的上游Observable变换为多个发送事件的Observables，然后将它们发射的事件合并后放进一个单独的Observable里。
- 我们还是再用一张图片来描述这个流程：
![rxjava2_flatmap.PNG](/upload/image/zlw/rxjava2_flatmap.PNG)

- 如图所示，上游每发送一个事件, flatMap都将创建一个新的水管, 然后发送转换之后的新的事件, 下游接收到的就是这些新的水管发送的数据.  这里需要注意的是, flatMap并不保证事件的顺序, 也就是图中所看到的, 并不是事件1就在事件2的前面。如果需要保证顺序则需要使用 concatMap （它的用法和flatMap一致）。

``` java
Observable.create(new ObservableOnSubscribe<Integer>() {           
            @Override
            public void subscribe(ObservableEmitter<Integer> emitter) throws Exception {
                emitter.onNext(1);
                emitter.onNext(2);
                emitter.onNext(3);
            }
        }).flatMap(new Function<Integer, ObservableSource<String>>() {            
            @Override
            public ObservableSource<String> apply(Integer integer) throws Exception {                
                final List<String> list = new ArrayList<>();                
                for (int i = 0; i < 3; i++) {
                    list.add("I am value " + integer);
                }                
                return Observable.fromIterable(list).delay(10,TimeUnit.MILLISECONDS);
            }
        }).subscribe(new Consumer<String>() {            
            @Override
            public void accept(String s) throws Exception {
                Log.d(TAG, s);
            }
        });
```
- 代码的运行结果是：

``` file
D/TAG: I am value 1
D/TAG: I am value 1
D/TAG: I am value 1
D/TAG: I am value 3
D/TAG: I am value 3
D/TAG: I am value 3
D/TAG: I am value 2
D/TAG: I am value 2
D/TAG: I am value 2
```
- 从运行结果来看，也说明flatmap的运行结果是无序的。

## 总结
- 上面大致介绍了Rxjava2的一些变化，逻辑是混乱的，因为我也是边学习，边记录。后面熟悉之后再回过头来整理一遍。
