---
title: Rxjava介绍和原理分析（基于1.x）
date: 2016-12-7 14:49:23
tags: Rxjava, 异步
categories: Android开源库
---
## 背景
### 简介
- Rxjava现在已经更新到2.x，变动还是比较大的，新增了一些概念，比如：backpressure。但是本文是参考一位大牛讲解rxjava的原理，采用的还是1.x的代码。
- 这里我们主要来看rxjava是怎么设计的。

### github地址
- [Rxjava](https://github.com/ReactiveX/RxJava)
- [RxAndroid](https://github.com/ReactiveX/RxAndroid)

### Rxjava 是什么
- 一个词：异步。Rxjava在github上面的介绍是：a library for composing asynchronous and event-based programs using observable sequences for the Java VM。大意是在jVM上面的一个异步的库，用observable实现。
- 它最大的好处也可以总结成一个词：简洁。这里的简洁指的是代码逻辑的简洁而非代码量的简洁。（因为我们都知道用框架是有代价的），Rxjava的简洁可以随着代码逻辑的复杂依然保持简洁。

## API介绍和原理简析

### Rxjava的观察者模式
- RxJava 有四个基本概念：Observable (可观察者，即被观察者)、 Observer (观察者)、 subscribe (订阅)、事件。Observable 和 Observer 通过 subscribe() 方法实现订阅关系，从而 Observable 可以在需要的时候发出事件来通知 Observer。
- 与传统观察者模式不同， RxJava 的事件回调方法除了普通事件 onNext() （相当于 onClick() / onEvent()）之外，还定义了两个特殊的事件：onCompleted() 和 onError()。
>1. onCompleted()：事件队列完结。Rxjava不仅把每个事件单独处理，还会把他们看作是一个队列，Rxjava规定，当不会再有新的onNext()发出时，需要触发onCompleted()方法作为标志。
>2. onError(): 事件队列异常：在事件处理过程中出现异常时，onError会被触发，同时队列自动终止，不允许再有事件发出。
>3. 在一个正确运行的事件序列中，onCompleted()和onError有且只有一个，并且是事件序列中的最后一个。

- Rxjava的观察者模型如下图：
![Rxjava观察者模型](/upload/image/zlw/观察者模型.jpg)

### 基本实现
#### 创建observer

``` java
Observer<String> observer = new Observer<String>() {
    @Override
    public void onNext(String s) {
        Log.d(tag, "Item: " + s);
    }

    @Override
    public void onCompleted() {
        Log.d(tag, "Completed!");
    }

    @Override
    public void onError(Throwable e) {
        Log.d(tag, "Error!");
    }
};
```

- 除了Observer接口之外，Rxjava还内置了一个实现了Observer的抽象类：Subscriber。Subscriber对Observer接口进行了一些扩展，但是他们的用法一样：

``` java
Subscriber<String> subscriber = new Subscriber<String>() {
    @Override
    public void onNext(String s) {
        Log.d(tag, "Item: " + s);
    }

    @Override
    public void onCompleted() {
        Log.d(tag, "Completed!");
    }

    @Override
    public void onError(Throwable e) {
        Log.d(tag, "Error!");
    }
};
```
- 其实，实质上，在Rxjava的subscribe过程中，Observer也总是会先被转换成一个Subscriber再使用，他们之间的区别如下：
>1. onStart(): 这是 Subscriber 增加的方法。它会在 subscribe 刚开始，而事件还未发送之前被调用，可以用于做一些准备工作，例如数据的清零或重置。
>2. unsubscribe(): 这是 Subscriber 所实现的另一个接口 Subscription 的方法，用于取消订阅。在这个方法被调用后，Subscriber 将不再接收事件。一般在这个方法调用前，可以使用 isUnsubscribed() 先判断一下状态。 unsubscribe() 这个方法很重要，因为在 subscribe() 之后， Observable 会持有 Subscriber 的引用，这个引用如果不能及时被释放，将有内存泄露的风险。所以最好保持一个原则：要在不再使用的时候尽快在合适的地方（例如 onPause() onStop() 等方法中）调用 unsubscribe() 来解除引用关系，以避免内存泄露的发生。

#### 创建Observable
- Observable即被观察者，它决定什么时候触发事件以及触发怎样的事件。Rxjava使用create()方法来创建一个Observable：

``` java
Observable observable = Observable.create(new Observable.OnSubscribe<String>() {
    @Override
    public void call(Subscriber<? super String> subscriber) {
        subscriber.onNext("Hello");
        subscriber.onNext("Hi");
        subscriber.onNext("Aloha");
        subscriber.onCompleted();
    }
});
```
- 这里传入OnSubscribe对象作为参数。它相当于一个计划表，当Observable被订阅的时候，call方法会被调用。
- 对于上面的例子，观察者Subscriber将会被调用三次onNext()和一次onCompleted()。这也就是观察者模型。对比下Android里面setOnclickListener就不难理解这里的代码逻辑了。

#### 其他用法

create() 方法是 RxJava 最基本的创造事件序列的方法。基于这个方法， RxJava 还提供了一些方法用来快捷创建事件队列，例如：
- just(T...):将传入的参数依次发送出来。

``` java
Observable observable = Observable.just("Hello", "Hi", "Aloha");
// 将会依次调用：
// onNext("Hello");
// onNext("Hi");
// onNext("Aloha");
// onCompleted();
```
- from(T[])/from(Iterable<? extends T>):将传入的数组或者Iterable拆分成具体对象后，依次发送出来。

``` java
String[] words = {"Hello", "Hi", "Aloha"};
Observable observable = Observable.from(words);
// 将会依次调用：
// onNext("Hello");
// onNext("Hi");
// onNext("Aloha");
// onCompleted();
```

#### Subscribe（订阅）
- 创建了Observable和Observer之后，再用subscribe()方法将它们联结起来，整条链子就可以工作了。：

``` java
observable.subscribe(observer);
// 或者：
observable.subscribe(subscriber);
```
- Observable.subscribe(Subscriber) 的内部实现是这样的（仅核心代码）：

``` java
// 注意：这不是 subscribe() 的源码，而是将源码中与性能、兼容性、扩展性有关的代码剔除后的核心代码。
// 如果需要看源码，可以去 RxJava 的 GitHub 仓库下载。
public Subscription subscribe(Subscriber subscriber) {
    subscriber.onStart();
    onSubscribe.call(subscriber);
    return subscriber;
}
```
- 可以看到，subscriber()做了3件事：
>1. 调用Subscriber.onStart().
>2. 调用Obserable中的OnSubscribe.call(subscriber)。从这也可以看出，在 RxJava 中， Observable 并不是在创建的时候就立即开始发送事件，而是在它被订阅的时候，即当 subscribe() 方法执行的时候。
>3. 将传入的Subscriber作为Subscription返回，这是为了方便unsubscribe()。

- 整个过程中对象间关系：
![对象间关系](/upload/image/zlw/订阅关系.gif)

- 除了subscribe(Observer) 和 subscribe(Subscriber)，Subscribe()还支持不完整定义的回调，Rxjava会自动根据定义创建出Subscriber。形式如下：

``` java
Action1<String> onNextAction = new Action1<String>() {
    // onNext()
    @Override
    public void call(String s) {
        Log.d(tag, s);
    }
};
Action1<Throwable> onErrorAction = new Action1<Throwable>() {
    // onError()
    @Override
    public void call(Throwable throwable) {
        // Error handling
    }
};
Action0 onCompletedAction = new Action0() {
    // onCompleted()
    @Override
    public void call() {
        Log.d(tag, "completed");
    }
};

// 自动创建 Subscriber ，并使用 onNextAction 来定义 onNext()
observable.subscribe(onNextAction);
// 自动创建 Subscriber ，并使用 onNextAction 和 onErrorAction 来定义 onNext() 和 onError()
observable.subscribe(onNextAction, onErrorAction);
// 自动创建 Subscriber ，并使用 onNextAction、 onErrorAction 和 onCompletedAction 来定义 onNext()、 onError() 和 onCompleted()
observable.subscribe(onNextAction, onErrorAction, onCompletedAction);
```

### 线程控制———scheduler
- 在不指定线程的情况下， RxJava 遵循的是线程不变的原则，即：在哪个线程调用 subscribe()，就在哪个线程生产事件；在哪个线程生产事件，就在哪个线程消费事件。如果需要切换线程，就需要用到 Scheduler （调度器）。
>1. Schedulers.immediate() 直接在当前线程运行，相当于不指定线程。这是默认的Scheduler
>2. Schedulers.newThread(): 总是启用新线程，并在新线程执行操作。
>3. Schedulers.io(): I/O 操作（读写文件、读写数据库、网络信息交互等）所使用的 Scheduler。行为模式和 newThread() 差不多，区别在于 io() 的内部实现是是用一个无数量上限的线程池，可以重用空闲的线程，因此多数情况下 io() 比 newThread() 更有效率。
>4. Schedulers.computation(): 计算所使用的 Scheduler。这个计算指的是 CPU 密集型计算，即不会被 I/O 等操作限制性能的操作，例如图形的计算。这个 Scheduler 使用的固定的线程池，大小为 CPU 核数。不要把 I/O 操作放在 computation() 中，否则 I/O 操作的等待时间会浪费 CPU。
>5. 另外， Android 还有一个专用的 AndroidSchedulers.mainThread()，它指定的操作将在 Android 主线程运行。

- 有了Scheduler，我们可以使用subscribeOn()和observeOn()两个方法来对线程控制。 subscribeOn()用于指定subscribe()所发生的线程，即Observable.OnSubscribe被激活时所处的线程（事件产生的线程。）observeOn()用于执行Subscriber所运行的线程（事件消费的线程）。
- 举一个很常见的例子（后台取数据，主线程展示）：

``` java
int drawableRes = ...;
ImageView imageView = ...;
Observable.create(new OnSubscribe<Drawable>() {
    @Override
    public void call(Subscriber<? super Drawable> subscriber) {
        Drawable drawable = getTheme().getDrawable(drawableRes));
        subscriber.onNext(drawable);
        subscriber.onCompleted();
    }
})
.subscribeOn(Schedulers.io()) // 指定 subscribe() 发生在 IO 线程
.observeOn(AndroidSchedulers.mainThread()) // 指定 Subscriber 的回调发生在主线程
.subscribe(new Observer<Drawable>() {
    @Override
    public void onNext(Drawable drawable) {
        imageView.setImageDrawable(drawable);
    }

    @Override
    public void onCompleted() {
    }

    @Override
    public void onError(Throwable e) {
        Toast.makeText(activity, "Error!", Toast.LENGTH_SHORT).show();
    }
});
```

### 变换
- Rxjava提供了对事件序列进行变换的支持，这是它的核心功能之一。所谓变换，就是将事件序列中的对象或整个序列进行加工处理，转换成不同的事件或时间序列

#### API
##### map()
- 首先看一个map()的例子：

``` java
Observable.just("images/logo.png") // 输入类型 String
    .map(new Func1<String, Bitmap>() {
        @Override
        public Bitmap call(String filePath) { // 参数类型 String
            return getBitmapFromPath(filePath); // 返回类型 Bitmap
        }
    })
    .subscribe(new Action1<Bitmap>() {
        @Override
        public void call(Bitmap bitmap) { // 参数类型 Bitmap
            showBitmap(bitmap);
        }
    });
```
- Func1 类似于Action1。也是Rxjava的接口，用于包装含有一个参数的方法。它们的区别是：Func1包装的是有返回值的方法，而Actionx是没有返回值的。
- map()方法将参数中的String对象转换成一个Bitmap对象后返回，而经过map()方法后，事件的参数类型也由string转为了Bitmap。因此它是对事件对象的转换。

##### flatmap()
- 先从一个例子来看这个问题,假设有一个数据结构『学生』，现在需要打印出一组学生的名字。实现方式很简单：：

``` java
Student[] students = ...;
Subscriber<String> subscriber = new Subscriber<String>() {
    @Override
    public void onNext(String name) {
        Log.d(tag, name);
    }
    ...
};
Observable.from(students)
    .map(new Func1<Student, String>() {
        @Override
        public String call(Student student) {
            return student.getName();
        }
    })
    .subscribe(subscriber);
```
- 再假设：如果要打印出每个学生所需要修的所有课程的名称呢？（需求的区别在于，每个学生只有一个名字，但却有多个课程。）

``` java
Student[] students = ...;
Subscriber<Course> subscriber = new Subscriber<Course>() {
    @Override
    public void onNext(Course course) {
        Log.d(tag, course.getName());
    }
    ...
};
Observable.from(students)
    .flatMap(new Func1<Student, Observable<Course>>() {
        @Override
        public Observable<Course> call(Student student) {
            return Observable.from(student.getCourses());
        }
    })
    .subscribe(subscriber);
```
- flatMap()和map()有一个共同点：将传入的参数转化后返回另一个对象，不同之处在于：flatMap()返回的是个Observable对象，并且Observable对象并不是被直接发送到Subscriber的回调方法中。
- flatMap()原理如下：
>1. 使用传入的事件对象创建一个Observable对象。
>2. 并不发送这个Observable对象，而是将它激活，于是它开始发送事件。
>3. 每一个创建的Observable发送的事件，都被汇入同一个Observable，而这个Observable负责将这些事件统一交给Subscribe的回调方法。

- 下图是flatmap的示意图：
![flatmap](/upload/image/zlw/flatmap.jpg)

- throttleFirst(): 在每次事件触发后的一定时间间隔内丢弃新的事件。常用作去抖动过滤，例如按钮的点击监听器： RxView.clickEvents(button) // RxBinding 代码，后面的文章有解释 .throttleFirst(500, TimeUnit.MILLISECONDS) // 设置防抖间隔为 500ms .subscribe(subscriber);

##### 变换的原理lift()
-  变换虽然功能不同，但实质上都是*针对事件序列的处理和再发送*，在Rxjava内部，它们基于同一个基础的变换方法list(Operator)：

``` java
// 注意：这不是 lift() 的源码，而是将源码中与性能、兼容性、扩展性有关的代码剔除后的核心代码。
// 如果需要看源码，可以去 RxJava 的 GitHub 仓库下载。
public <R> Observable<R> lift(Operator<? extends R, ? super T> operator) {
    return Observable.create(new OnSubscribe<R>() {
        @Override
        public void call(Subscriber subscriber) {
            Subscriber newSubscriber = operator.call(subscriber);
            newSubscriber.onStart();
            onSubscribe.call(newSubscriber);
        }
    });
}
```
- 这段代码生成了一个新的Observable并返回，大家看这里call中的实现和上面讲过的Observable.subscribe()一样。其实是不一样的，不一样的地方在于onSubscribe.call(subscriber)中的onSubscribe所指代的对象不同。
- subscribe() 中这句话的 onSubscribe 指的是 Observable 中的 onSubscribe 对象，这个没有问题，但是 lift() 之后的情况就复杂了点。
- 当含有 lift() 时：
>1. lift()创建了一个Observable后，加上之前的原始Observable，已经有两个Observable。
>2. 同样的，新Observable里的新OnSubscribe加上之前的原始Observable中的原始OnSubscribe,也有两个Onsubscribe
>3. 当用户调用经过lift()后的Observable的Subscribe()时，使用的时lift()所返回的新的Observable，于是它所触发的onSubscribe.call(subscriber),使用的也是新Observable中的新Onsubscribe,即lift()中生成的那个Onsubscribe。
>4. 而这个新 OnSubscribe 的 call() 方法中的 onSubscribe ，就是指的原始 Observable 中的原始 OnSubscribe ，在这个 call() 方法里，新 OnSubscribe 利用 operator.call(subscriber) 生成了一个新的 Subscriber（Operator 就是在这里，通过自己的 call() 方法将新 Subscriber 和原始 Subscriber 进行关联，并插入自己的『变换』代码以实现变换），然后利用这个新 Subscriber 向原始 Observable 进行订阅。

- lift()过程，有点像一种代理机制，通过事件拦截和处理时限事件序列的变换。
- 精简的说：在 Observable 执行了 lift(Operator) 方法之后，会返回一个新的 Observable，这个新的 Observable 会像一个代理一样，负责接收原始的 Observable 发出的事件，并在处理后发送给 Subscriber。
- 如下所示是lift工作流程：
![rxjava_lift](/upload/image/zlw/rxjava_lift.gif)

#### compose:对Observable整体的变换
- 除了 lift() 之外， Observable 还有一个变换方法叫做 compose(Transformer)。它和 lift() 的区别在于， lift() 是针对事件项和事件序列的，而 compose() 是针对 Observable 自身进行变换。

``` java
public class LiftAllTransformer implements Observable.Transformer<Integer, String> {
    @Override
    public Observable<String> call(Observable<Integer> observable) {
        return observable
            .lift1()
            .lift2()
            .lift3()
            .lift4();
    }
}
...
Transformer liftAll = new LiftAllTransformer();
observable1.compose(liftAll).subscribe(subscriber1);
observable2.compose(liftAll).subscribe(subscriber2);
observable3.compose(liftAll).subscribe(subscriber3);
observable4.compose(liftAll).subscribe(subscriber4);
```
- 使用compose()方法，Observable可以利用传入的Transformer对象的call方法直接对自身进行处理。

### 线程控制：Scheduler (二)
#### Scheduler 的 API (二)
- 前面提到，利用subscribeOn() 结合 observeOn() 来实现线程控制，让事件的产生和消费发生在不同的线程。那么能不能多次切换线程呢？
- observeOn()可以多次调用实现线程的切换，但是subscribeOn()却只能被调用一次。

#### Scheduler 的原理（二）
- subscribeOn() 和 observeOn() 的内部实现，也是用的 lift()。
- subscribeOn() 原理图如下：
![rxjava_subscribeOn](/upload/image/zlw/rxjava_subscribeOn.jpg)

- observeOn() 原理图：
![rxjava_observeOn](/upload/image/zlw/rxjava_observeOn.jpg)

- 从图中可以看出，subscribeOn() 和 observeOn() 都做了线程切换的工作（图中的 "schedule..." 部位）。不同的是， subscribeOn() 的线程切换发生在 OnSubscribe 中，即在它通知上一级 OnSubscribe 时，这时事件还没有开始发送，因此 subscribeOn() 的线程控制可以从事件发出的开端就造成影响；而 observeOn() 的线程切换则发生在它内建的 Subscriber 中，即发生在它即将给下一级 Subscriber 发送事件时，因此 observeOn() 控制的是它后面的线程。

- 用一张图来解释多个subscribeOn() 和 observeOn()混合使用时，线程调度情况：
![rxjava_subscribeon_observeOn.jpg](/upload/image/zlw/rxjava_subscribeon_observeOn.jpg)
- 图中共有 5 处含有对事件的操作。由图中可以看出，①和②两处受第一个 subscribeOn() 影响，运行在红色线程；③和④处受第一个 observeOn() 的影响，运行在绿色线程；⑤处受第二个 onserveOn() 影响，运行在紫色线程；而第二个 subscribeOn() ，由于在通知过程中线程就被第一个 subscribeOn() 截断，因此对整个流程并没有任何影响。这里也就回答了前面的问题：当使用了多个 subscribeOn() 的时候，只有第一个 subscribeOn() 起作用。


#### 延伸：doOnSubscribe()
- 前面讲到过Subscriber.onStart()方法是在发送事件之前执行，可以用作流程开始前的初始化，不过由于它不能指定自己工作的线程，因此如果需要在主线程执行的代码（比如弹一个进度框），那么就会有可能出错。
- 而与 Subscriber.onStart() 相对应的，有一个方法 Observable.doOnSubscribe() 。它和 Subscriber.onStart() 同样是在 subscribe() 调用后而且在事件发送前执行，但区别在于它可以指定线程。默认情况下， doOnSubscribe() 执行在 subscribe() 发生的线程；而如果在 doOnSubscribe() 之后有 subscribeOn() 的话，它将执行在离它最近的 subscribeOn() 所指定的线程。比如：

``` java
Observable.create(onSubscribe)
    .subscribeOn(Schedulers.io())
    .doOnSubscribe(new Action0() {
        @Override
        public void call() {
            progressBar.setVisibility(View.VISIBLE); // 需要在主线程执行
        }
    })
    .subscribeOn(AndroidSchedulers.mainThread()) // 指定主线程
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(subscriber);

```

## 总结

*上面我基本是从大神的文章中记录过来的，总结来说主要有两点：*
- 异步：两个比较核心的方法subscribeOn和observeOn这两个方法都传入一个Scheduler对象，subscribeOn指定发射事件的线程，observeOn指定消费事件的线程。
- 强大的操作方法：转换操作符，如map(),flatMap(),filter(),merge(),concat(),lift(),compose()等操作符可以将事件序列中的对象或整个序列进行加工处理，转换成不同的事件或事件序列，然后再发射出去！


## 参考资料
- 本文是参考[给 Android 开发者的 RxJava 详解](http://gank.io/post/560e15be2dca930e00da1083)，  不过它是结合1.x来写的，2.x的rxjava变化还是不小的。但是万变不离其宗，它的核心思想和原理是不会变的。
