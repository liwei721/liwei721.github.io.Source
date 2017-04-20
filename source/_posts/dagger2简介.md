---
title: dagger2简介
date: 2017-04-07 15:53:33
tags: dagger
categories: Android开源库
---

## 背景
- 本文参考[Dagger2 最清晰的使用教程](http://www.jianshu.com/p/24af4c102f62)
- [Android：dagger2让你爱不释手-基础依赖注入框架篇](http://www.jianshu.com/p/cd2c1c9f68d4)
- [依赖注入](http://codethink.me/2015/08/01/dependency-injection-theory/)

### 概念
- dagger2是一个基于JSR-330标准的依赖注入框架，在编译期间自动生成代码，负责依赖对象的创建。
- JSR即Java Specification Requests，意思是java规范提要。而JSR-330则是 Java依赖注入标准。

### 需要了解的原则
#### 依赖倒置原则(IOC)
- 高层次的模块不应该依赖于低层次的模块，他们都应该依赖于抽象。
- 抽象不应该依赖于具体实现，具体实现应该依赖于抽象。
- 参考[使用Dagger2前你必须了解的一些设计原则](http://www.jianshu.com/p/cc1427e385b5),有个关于下拉刷新的例子。可以很好的说明这个情况。

#### 依赖注入
- 依赖注入是目标类（目标类需要进行依赖初始化的类）中所依赖的其他类的初始化过程，不是通过手动编码的方式创建，而是通过技术手段可以把其他的类的已经初始化好的实例自动注入到目标类中。
- 目的是为了让目标类和依赖进一步解耦。dagger2是一种实现依赖注入的手段。
![dagger2_di.PNG](/upload/image/zlw/dagger2_di.PNG)

## 实现

### Inject 是什么
- 先看一段代码：

``` java
class A{
       B b = new B(...);
       C c = new C();
       D d = new D(new E());
       F f = new F(.....);
 }
```
- 上面的代码没有任何问题，不过我们在想，能不能用一种自动化、更省力的方法解决这种需要手动new对象的情况，把精力集中到重要的业务上。
- 我们可以用注解（Annotation/ inject）来标注目标类中所依赖的其他类，同样用注解来标注所依赖的其他类的构造方法。

``` java
class A{
       @Inject
       B b;
  }

  class B{
      @Inject
      B(){
      }
  }
```
- 但是想要依赖的类和类的构造之间发生关联，我们还需要一个桥梁把他们连接起来，那这个桥梁就是Component。
![dagger2_component.PNG](/upload/image/zlw/dagger2_component.PNG)

### Component是什么
- Component也是一个注解类，需要用@Component来标注类，不过 **该类是接口或抽象类**。
- Component是这么工作的：Component需要引用到目标类的实例，Component会查找目标类中用Inject标注的属性，然后查找对应的属性后会接着查找该属性对应的用Inject标注的构造函数（联系）。接下来就是初始化该属性的实例并把实例赋值到目标类中，因此Component也可以叫做注入器（Injector）。
![dagger2_component2.PNG](/upload/image/zlw/dagger2_component2.PNG)

- 我们这里贴一段编译自动生成的Component代码(相关的解释，我都注释)：

``` java
public final class DaggerMainActivityComponent implements MainActivityComponent {
  // 一个@Inject，会被生成一个Factory，而Factory继承了Provider
  private Provider<Pot> potProvider;

  // 目标类（这里就是MainActivity）@Inject的属性或方法注解，对应的是MainActivity_MembersInjector类，它里面有个方法injectMembers，通过这个方法建立上面提到的连接。
  private MembersInjector<MainActivity> mainActivityMembersInjector;

  private DaggerMainActivityComponent(Builder builder) {
    assert builder != null;
    initialize(builder);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static MainActivityComponent create() {
    return builder().build();
  }

  @SuppressWarnings("unchecked")
  private void initialize(final Builder builder) {

    this.potProvider = Pot_Factory.create(Rose_Factory.create());

    this.mainActivityMembersInjector = MainActivity_MembersInjector.create(potProvider);
  }

  @Override
  public void inject(MainActivity activity) {
    // 这里其实就是将Inject构造的依赖类的实例赋值给目标类
    mainActivityMembersInjector.injectMembers(activity);
  }

  public static final class Builder {
    private Builder() {}

    public MainActivityComponent build() {
      return new DaggerMainActivityComponent(this);
    }
  }
}
```

### Module是个啥
- 现在又出现一种情况：我们项目中使用了第三方的类库，第三方的类库我们不可能去修改添加Inject注解，这样在目标类中的Inject就失效了。
- Module就是为解决这样的问题而出现的，我们可以封装第三方的类库，然后将代码放入Module中：

``` java
@Module
   public class ModuleClass{
         //A是第三方类库中的一个类
         A provideA(){
              return A();
         }
   }
```
- 这样看来，Module其实是一个对象工厂，Module中的方法其实就是创建类实例的方法。接下来要解决另外一个问题：Module怎么和Component发生联系。

#### Component && Module
- 上面提到过，Component是注入器，它一端连接目标类，另一端连接目标类依赖实例，它把目标类所依赖的实例注入到目标类中，那么Module应该是属于Component的实例端的。那么Component就需要管理Module，Component中的modules属性可以把Module加入Component，modules可以加入多个Module。
- 还有一个问题是怎么把Module中的各种创建类的方法与目标类中的用Inject注解标注的依赖产生关联。这时候就要用到Providers。

#### providers
- Module中的创建类实例方法用Provides进行标注，Component在搜索到目标类中Inject注解属性后，Component会去Module中查找用Providers标注的对应的创建类实例的方法。


### 小结
- Inject主要用来标注目标类的依赖和依赖类的构造函数。
- Component是一个桥梁，一段是目标类，另一端是目标类所依赖的实例，它也是注入器（Injector）负责把目标类所依赖的实例注入到目标类中，同时也管理Module。
- Module和Providers是为解决第三方类库而生的，Module是一个简单工厂模式，Module可以包含创建类实例的方法，这些方法用Providers来标注。
![dagger2_sumary.PNG](/upload/image/zlw/dagger2_sumary.PNG)

### Qualifier(限定符)是什么
- 上面提到过Component将Inject或者Module创建的实例注入到目标类中，创建类实例有2个维度可以创建：
>1. 通过Inject 注解标注的构造函数来创建（Inject维度）。
>2. 通过工厂模式的Module来创建（Module维度）。

- 上面两个维度有优先级之分，Component会首先从Module维度中查找实例，若找到就用Module维度创建类实例，并停止查找Inject维度。所以Module维度要高于Inject维度。
- 现在有个问题，基于同一个维度条件下，若一个类的实例有多种方法可以创建出来，那么注入器（Component）应该选择哪种方法来创建该类的实例。这时候就需要用Qualifier来解决这个问题。
- 我们下面来看个例子：

``` java
/**
  自定义一个类有两个构造方法
*/
public class Apple {
    String color;

    public Apple() {
        Log.e("TAG", "我是一个普通的苹果");

    }

    public Apple(String color) {
        this.color = color;
        Log.e("TAG", "我是一个有颜色的苹果");

    }

}

/**
 * 自定义一个限定符
 */
@Qualifier//限定符
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Type {
    String value() default "";//默认值为""
}
```

``` java
/**
  定义一个Module，在Module中定义生成实例的方法
*/
@Module
public class SaladModule {

   .........

    @Type("normal")//使用限定符来区别使用哪个构造函数返回对象
    @Provides
    public Apple provideNormalApple() {
        return new Apple();
    }

    @Type("color")//使用限定符来区别使用哪个构造函数返回对象
    @Provides
    public Apple provideColorApple(String color) {
        return new Apple(color);
    }

    //    由于我们的Apple构造函数里使用了String,所以这里要管理这个String(★否则报错)
    //    int等基本数据类型是不需要这样做的
    @Provides
    public String provideString(){
        return new String();
    }
}
```
``` java
/**
  定义Component，用于Inject实例对象到目标类中
*/
@Component(modules = {SaladModule.class})//指明要在那些Module里寻找依赖
public interface SaladComponent {

    .........

    @Type("normal")//关键靠这个限定符来区分调用哪个构造函数
    Apple provideNormalApple();//这个方法名并不重要，只要是provide开头就行，但是建议和module里一致

    @Type("color")
    Apple provideColorApple();


    String provideString();
    //注意：下面的这个方法，表示要将以上的三个依赖注入到某个类中
    //这里我们把上面的三个依赖注入到Salad中
    //因为我们要做沙拉
    void inject(Salad salad);

}
```
- 接下来就是目标类中使用：

``` java
public class Salad {

  .........

    @Inject
    @Type("normal")
    Apple normalApple;

    @Inject
    @Type("color")
    Apple colorApple;

    public Salad() {
        // DaggerSaladComponent编译时才会产生这个类，
        // 所以编译前这里报错不要着急(或者现在你先build一下)
        SaladComponent saladComponent = DaggerSaladComponent.create();
        saladComponent.inject(this);//将saladComponent所连接的SaladModule中管理的依赖注入本类中
        makeSalad(pear, banana, saladSauce);
    }

    private void makeSalad(Pear pear, Banana banana, SaladSauce saladSauce) {
        Log.e("TAG", "我在搅拌制作水果沙拉");
    }
}
```
- 如上面代码所示：Qualifier 其实是要自定义一个标识，然后在需要区分生成实例的方法上面进行标注，这样就可以区分生成怎样的实例。
- 另外还有个注解标识 Named，它是使用的String来标识，写String容易拼写错误，且没有Qualifier灵活，所以我们一般都用Qualifier。

### Component组织方式
- 上面主要是对Dagger2中的一些重要概念做了描述，但对于在实际开发中，怎么运用Dagger2，是我们重点关注的。

#### app中根据什么来划分Component
- 一个app中如果只有一个Component，那这个Component是很难维护的、并且变化会很频繁，引入代价就有点大。这是因为Component的职责太多了，所以我们有必要对Component进行划分，划分为粒度，划分为粒度更小的Component，划分的规则如下：
>1. 要有一个全局的Component（可以叫ApplicationComponent），负责管理整个app的全局类实例（全局类实例整个app都要用到的类实例，这些类基本都是单例的）。
>2. 每个页面对应一个Component，比如一个Activity页面定义一个Component，一个Fragment定义一个Component。当然这不是必须的，有些页面之间的依赖的类是一样的，可以公用一个Component。

- 为啥要以页面为单位来划分Component呢？
>1. 一个app是由很多个页面组成的，从组成app的角度来看一个页面就是一个完整的最小粒度了。
>2. 一个页面的实现其实是要依赖各种类的，可以理解成一个页面把各种依赖的类组织起来共同实现一个大的功能，每个页面都组织着自己的需要依赖的类，一个页面就是一堆类的组织者。
>3. 划分粒度不能太小了。假如使用mvp架构搭建app，划分粒度是基于每个页面的m、v、p各自定义Component的，那Component的粒度就太小了，定义这么多的Component，管理、维护就很非常困难。

#### Singleton
- 上面提到一个app要有一个全局的Component（ApplicationComponent），它负责管理整个app用到的全局类实例，那么这些全局类实例应该是单例的。
- 我们想到了Module，Module里面可以包含很多创建类实例的方法，且Component会首先从Moudle中查找类实例。所以想要实现全局类实例，可以这么做：
>1. 在Module中定义创建全局类实例的方法。
>2. Application 管理Module。
>3. 保证ApplicationComponent只有一个实例，（在app的Application中实例化ApplicationComponent）

- Singleton的作用如下：
>1. 更好的管理ApplicationComponent和Module之间的关系，保证ApplicationComponent和Module是匹配的，若ApplicationComponent和Module的Scope是不一样的，则编译时报错。
>2. 代码可读性，让开发者更好的了解Module中创建的实例类是单例。（其本身是不能创建单例的）。

### 组织Component
- 上面我们将app划分为不同的Component，全局类型的实例也创建了单例模式。如果其他的Component想要把全局的实例注入到目标类中该怎么办呢？
- 这涉及到类实例共享的问题，因为Component有管理创建类实例的能力，因此只要能很好的组织Component之间的关系。就能规划好app中的Component。

#### 依赖方式
- 一个Component是依赖于一个或多个Component，Component中的dependencies属性就是依赖方式的具体实现。

#### 包含方式
- 一个Component是包含一个或多个Component的，被包含的Component还可以继续包含其他的Component，这种方式特别像Activity与Fragment的关系。SubComponent就是包含方式的具体实现。

#### 例子
- 我们来看一个dependencies的例子：

``` java
public abstract class Flower {
    public abstract String whisper();
}

public class Lily extends Flower {

    @Override
    public String whisper() {
        return "纯洁";
    }
}

public class Rose extends Flower {

    public String whisper()  {
        return "热恋";
    }
}
```
- 接下来就是创建一个Module

``` java
@Module
public class FlowerModule {

    @Provides
    @RoseFlower
    Flower provideRose() {
        return new Rose();
    }

    @Provides
    @LilyFlower
    Flower provideLily() {
        return new Lily();
    }
}
```
- Component上面也需要指定@Qualifier

``` java
@Component(modules = FlowerModule.class)
public interface FlowerComponent {
    @RoseFlower
    Flower getRoseFlower();

    @LilyFlower
    Flower getLilyFlower();
}
```
``` java
public class Pot {

    private Flower flower;

    public Pot(Flower flower) {
        this.flower = flower;
    }

    public String show() {
        return flower.whisper();
    }
}
```
- PotModule需要依赖Flower，需要指定其中一个子类实现，这里使用RoseFlower:

``` java
@Module
public class PotModule {

    @Provides
    Pot providePot(@RoseFlower Flower flower) {
        return new Pot(flower);
    }
}
```
``` java
@Component(modules = PotModule.class,dependencies = FlowerComponent.class)
public interface PotComponent {
    Pot getPot();
}
```
``` java
@Component(dependencies = PotComponent.class)
public interface MainActivityComponent {
    void inject(MainActivity activity);
}
```

``` java
public class MainActivity extends AppCompatActivity {

    @Inject
    Pot pot;

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DaggerMainActivityComponent.builder()
                .potComponent(DaggerPotComponent.builder()
                        .flowerComponent(DaggerFlowerComponent.create())
                        .build())
                .build().inject(this);

        String show = pot.show();
        Toast.makeText(MainActivity.this, show, Toast.LENGTH_SHORT).show();
    }
}

```
- 如果使用Subcomponent的话则是这么写， 其他类不需要改变，只修改Component即可:

``` java
@Component(modules = FlowerModule.class)
public interface FlowerComponent {

    PotComponent plus(PotModule potModule);
}
```
``` java
@Subcomponent(modules = PotModule.class)
public interface PotComponent {
    MainActivityComponent plus();
}
```

``` java
@Subcomponent
public interface MainActivityComponent {
    void inject(MainActivity activity);
}
```
``` java
public class MainActivity extends AppCompatActivity {

    @Inject
    Pot pot;

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DaggerFlowerComponent.create()
                .plus(new PotModule())  // 这个方法返回PotComponent
                .plus()                 // 这个方法返回MainActivityComponent
                .inject(this);

        String show = pot.show();
        Toast.makeText(MainActivity.this, show, Toast.LENGTH_SHORT).show();
    }
}
```
##### Component dependencies 和Subcomponent区别
- Component dependencies 能单独使用，而Subcomponent必须由Component调用方法获取。
- Component dependencies 可以很清楚的得知他依赖哪个Component， 而Subcomponent不知道它自己的谁的孩子
- 使用上的区别，Subcomponent就像这样DaggerAppComponent.plus(new SharePreferenceModule());
使用Dependence可能是这样DaggerAppComponent.sharePreferenceComponent(SharePreferenceComponent.create())

##### Component dependencies和Subcomponent使用上的总结
- Component Dependencies：
>1. 你想保留独立的想个组件（Flower可以单独使用注入，Pot也可以）
>2. 要明确的显示该组件所使用的其他依赖.

- Subcomponent:
>1. 两个组件之间的关系紧密
>2. 你只关心Component，而Subcomponent只是作为Component的拓展，可以通过Component.xxx调用。


#### 继承方式
- 官网没有提到该方式，具体没有提到的原因我觉得应该是，该方式不是解决类实例共享的问题，而是从更好的管理、维护Component的角度，把一些Component共有的方法抽象到一个父类中，然后子Component继承。、

### Scope
- 上面提到了Component的组织，那么Scope就是为了更好的管理和组织Component。
- 不管是依赖方式还是包含方式，都有必要用自定义Scope注解标注这些Component，编译器检查有依赖关系或包含关系的Component，若Component没有用自定义Scope标注，就会报错。
- 更好的管理Component与Module之前的匹配关系，编译器会检查Component管理的Modules，若发现标注Component的自定义Scope注解与Modules中的标注创建类实例方法的注解不一样，就会报错。
- 可读性提高，如用Singleton标注全局类，这样让开发者明白这是全局单例类。
![dagger2_scope.PNG](/upload/image/zlw/dagger2_scope.PNG)

## dagger2好处
- 提高开发效率、省去重复的简单体力劳动：首先new一个实例的过程是一个重复的简单体力劳动，dagger2完全可以把new一个实例的工作做了，因此我们把主要精力集中在关键业务上、同时也能增加开发效率上。
省去写单例的方法，并且也不需要担心自己写的单例方法是否线程安全，自己写的单例是懒汉模式还是饿汉模式。因为dagger2都可以把这些工作做了。
- 更好的管理类实例：每个app中的ApplicationComponent管理整个app的全局类实例，所有的全局类实例都统一交给ApplicationComponent管理，并且它们的生命周期与app的生命周期一样。每个页面对应自己的Component，页面Component管理着自己页面所依赖的所有类实例。因为Component，Module，整个app的类实例结构变的很清晰。
- 解耦：假如不用dagger2的话，一个类的new代码是非常可能充斥在app的多个类中的，假如该类的构造函数发生变化，那这些涉及到的类都得进行修改。设计模式中提倡把容易变化的部分封装起来。

## 项目中使用Dagger2注意点
- 这里重点说下dagger2对目标类进行依赖注入的过程，现在假设要初始化目标类中的其中一个依赖类的实例，那具体步骤就在下面：

``` file
步骤1：查找Module中是否存在创建该类的方法。
步骤2：若存在创建类方法，查看该方法是否存在参数
    步骤2.1：若存在参数，则按从**步骤1**开始依次初始化每个参数
    步骤2.2：若不存在参数，则直接初始化该类实例，一次依赖注入到此结束
步骤3：若不存在创建类方法，则查找Inject注解的构造函数，
           看构造函数是否存在参数
    步骤3.1：若存在参数，则从**步骤1**开始依次初始化每个参数
    步骤3.2：若不存在参数，则直接初始化该类实例，一次依赖注入到此结束
```

### 注意的点：
- 一个app必须要有一个Component（名字可以是ApplicationComponent）用来管理app的整个全局类实例
- 多个页面可以共享一个Component
- 不是说Component就一定要对应一个或多个Module，Component也可以不包含Module。
- 自定义Scope注解最好使用上，虽然不使用也是可以让项目运行起来的，但是加上好处多多。
