---
title: Rxjava介绍（进阶）
date: 2017-04-06 22:32:54
tags: Rxjava
categories: Android开源库
---
## 背景
- 在[Rxjava介绍和原理分析](https://liwei721.github.io/2016/12/07/RxJava介绍和原理分析/)中简单介绍了Rxjava的实现及部分原理，今天来点进阶的内容，方便更好的理解Rxjava的实现原理。
- 主要介绍下Rxjava的观察者模式怎么实现、操作符的实现。
- 这里讲解的代码还是1.x的代码，主要看原理实现。

## 关于观察者模式
- 这里举一个例子，台灯（观察者）和开关（被观察者）

``` file
//创建一个被观察者（开关）
 Observable switcher=Observable.create(new Observable.OnSubscribe<String>(){

            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.onNext("On");
                subscriber.onNext("Off");
                subscriber.onNext("On");
                subscriber.onNext("On");
                subscriber.onCompleted();
            }
        });
//创建一个观察者（台灯）
 Subscriber light=new Subscriber<String>() {
            @Override
            public void onCompleted() {
                //被观察者的onCompleted()事件会走到这里;
                Log.d("DDDDDD","结束观察...\n");
            }

            @Override
            public void onError(Throwable e) {
                    //出现错误会调用这个方法
            }
            @Override
            public void onNext(String s) {
                //处理传过来的onNext事件
                Log.d("DDDDD","handle this---"+s)
            }
//订阅
switcher.subscribe(light);
```
- 我们需要搞清楚3个问题:
>1. 被观察者中的Observable.OnSubscribe是什么，有什么用？
>2. call(subscriber)方法中，subscriber哪里来的?
>3. 为什么只有在订阅之后，被观察者才会开始发送消息？

- 首先来看OnSubscribe的代码：

``` java
//上一篇文章也提到Acton1这个接口，内部只有一个待实现call()方法
//没啥特别，人畜无害
public interface Action1<T> extends Action {
    void call(T t);
}
//OnSubscribe继承了这个Action1接口,
public interface OnSubscribe<T> extends Action1<Subscriber<? super T>> {
        // OnSubscribe仍然是个接口
    }
```

- 而在Observable观察者的类中，OnSubscribe是它唯一的属性,同时也是Observable构造函数中唯一必须传入的参数，也就是说，只要创建了Observable，那么内部也一定有一个OnSubscribe对象。
- 不过Observable对象时不能直接new的，需要通过create()、just()、form()方法来获取。这些方法本质上是去new了Observable对象。

``` java
public class Observable<T> {
    //唯一的属性
    final OnSubscribe<T> onSubscribe;
    //构造函数，因为protected，我们只能使用create函数
    protected Observable(OnSubscribe<T> f) {
        this.onSubscribe = f;
    }
    //create(onSubscribe) 内部调用构造函数。
    public static <T> Observable<T> create(OnSubscribe<T> f) {
        return new Observable<T>(RxJavaHooks.onCreate(f));
    }
    ....
    ....
    }
```

- 创建完对象，当进行订阅时，代码逻辑如下：

``` java
//传入了观察者对象
  public final Subscription subscribe(final Observer<? super T> observer) {
     ....
      //往下调用
      return subscribe(new ObserverSubscriber<T>(observer));
  }

  public final Subscription subscribe(Subscriber<? super T> subscriber) {
      //往下调用
      return Observable.subscribe(subscriber, this);
  }


  //调用到这个函数
static <T> Subscription subscribe(Subscriber<? super T> subscriber, Observable<T> observable) {
      // new Subscriber so onStart it
      subscriber.onStart();

      // add a significant depth to already huge call stacks.
      try {
          // 在这里简单讲，对onSubscribe进行封装，不必紧张。
          OnSubscribe onSubscribe=RxJavaHooks.onObservableStart(observable, observable.onSubscribe);

          //这个才是重点！！！
          //这个调用的具体实现方法就是我们创建观察者时
          //写在Observable.create()中的call()呀
          //而调用了那个call(),就意味着事件开始发送了
          onSubscribe.call(subscriber);
          //不信你往回看

          return RxJavaHooks.onObservableReturn(subscriber);
      } catch (Throwable e) {
          ....
          ....
          }
          return Subscriptions.unsubscribed();
      }
  }
```

- 从代码中我们可以回答上面提出的三个问题：
>1. onSubscribe是Observable内部唯一属性，是连接Observable和subscriber的关键，相当于连接台灯和开关的那根电线
>2. call(Subscriber<? super String> subscriber)中的subscriber，就是我们自己创建的那个观察者
>3. 有在订阅的时候，才会发生onSubscribe.call(subscriber)，进而才会开始调用onNext(),onComplete()等。

- 我们用一张图来表达上面的流程：
![rxjava_obserable.png](/upload/image/zlw/rxjava_obserable.png)

**通过图中的流程，我们也可以做一个总结：**
- 订阅这个动作，实际上是观察者(subscriber)对象把自己传递给被观察者(observable)内部的onSubscribe。
- onSubscribe的工作就是调用call(subscriber)来通知被观察者发送消息给这个subscriber。

## 关于操作符
- 我们直接以map为例来研究下操作符的工作流程，先看个例子：

``` file
Observable.create(new Observable.just(getFilePath())
            //使用map操作来完成类型转换
            .map(new Func1<String, Bitmap>() {
              @Override
              public Bitmap call(String s) {
                //显然自定义的createBitmapFromPath(s)方法，是一个极其耗时的操作
                  return createBitmapFromPath(s);
              }
          })
            .subscribe(
                 //创建观察者，作为事件传递的终点处理事件    
                  new Subscriber<Bitmap>() {
                        @Override
                        public void onCompleted() {
                            Log.d("DDDDDD","结束观察...\n");
                        }

                        @Override
                        public void onError(Throwable e) {
                            //出现错误会调用这个方法
                        }
                        @Override
                        public void onNext(Bitmap s) {
                            //处理事件
                            showBitmap(s)
                        }
                    );
```
- 我们看下map的代码：

``` java
public final <R> Observable<R> map(Func1<? super T, ? extends R> func) {
           //创建了全新代理的的Observable，构造函数传入的参数是OnSubscribe
           //OnSubscribeMap显然是OnSubscribe的一个实现类，
           //也就是说，OnSubscribeMap需要实现call()方法
           //构造函数传入了真实的Observable对象
           //和一个开发者自己实现的Func1的实例
       return create(new OnSubscribeMap<T, R>(this, func));
   }
```

- 上面代码是首先创建了一个Observable的代理对象。再看OnSubscribeMap的代码：

``` file
public final class OnSubscribeMap<T, R> implements OnSubscribe<R> {
    //用于保存真实的Observable对象
    final Observable<T> source;
    //还有我们传入的那个Func1的实例
    final Func1<? super T, ? extends R> transformer;

    public OnSubscribeMap(Observable<T> source, Func1<? super T, ? extends R> transformer) {
        this.source = source;
        this.transformer = transformer;
    }

    //实现了call方法，我们知道call方法传入的Subscriber
    //就是订阅之后，外部传入真实的的观察者
    @Override
    public void call(final Subscriber<? super R> o) {
        //把外部传入的真实观察者传入到MapSubscriber，构造一个代理的观察者
        MapSubscriber<T, R> parent = new MapSubscriber<T, R>(o, transformer);
        o.add(parent);
        //让外部的Observable去订阅这个代理的观察者
        source.unsafeSubscribe(parent);
    }

    //Subscriber的子类，用于构造一个代理的观察者
    static final class MapSubscriber<T, R> extends Subscriber<T> {
            //这个Subscriber保存了真实的观察者
        final Subscriber<? super R> actual;
        //我们自己在外部自己定义的Func1
        final Func1<? super T, ? extends R> mapper;

        boolean done;

        public MapSubscriber(Subscriber<? super R> actual, Func1<? super T, ? extends R> mapper) {
            this.actual = actual;
            this.mapper = mapper;
        }
        //外部的Observable发送的onNext()等事件
        //都会首先传递到代理观察者这里
        @Override
        public void onNext(T t) {
            R result;

            try {
                //mapper其实就是开发者自己创建的Func1，
                //call()开始变换数据
                result = mapper.call(t);
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                unsubscribe();
                onError(OnErrorThrowable.addValueAsLastCause(ex, t));
                return;
            }
            //调用真实的观察者的onNext()
            //从而在变换数据之后，把数据送到真实的观察者手中
            actual.onNext(result);
        }
        //onError()方法也是一样
        @Override
        public void onError(Throwable e) {
            if (done) {
                RxJavaHooks.onError(e);
                return;
            }
            done = true;

            actual.onError(e);
        }


        @Override
        public void onCompleted() {
            if (done) {
                return;
            }
            actual.onCompleted();
        }

        @Override
        public void setProducer(Producer p) {
            actual.setProducer(p);
        }
    }
}
```
- 上面代码量比较大，在代码中也加入了关键的注释。我们下面用一张图来描述这个流程：
![Rxjava_operator.png](/upload/image/zlw/Rxjava_operator.png)

- 总结来说就是：每一个操作符，都会创建一个代理观察者和一个代理被观察者。类似于java动态代理，对执行过程中的对象做处理。
