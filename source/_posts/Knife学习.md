---
title: Butter Knife学习
date: 2017-04-10 16:25:10
tags: butterknife
categories: Android开源库
---

## 简介
### 参考文章
- [Android Butter Knife 框架——最好用的View注入](http://www.jianshu.com/p/9ad21e548b69)
- [绝对不容错过，ButterKnife使用详谈](http://www.jianshu.com/p/b6fe647e368b#)

### 背景
- 在开发过程中，我们总是会写大量的findViewById和点击事件，像初始化View、设置View监听这样简单而重复的操作让人觉得麻烦，而ButterKnife就是为解决这种问题的开源框架。
- 对于一个成员变量使用@BindView注解，并传入一个View ID，ButterKnife能够帮助你找到对应的View，并自动的进行转换（将view转换为特定的子类）：

``` java
class ExampleActivity extends Activity {
    @BindView(R.id.title)  TextView title;
    @BindView(R.id.subtitle) TextView subtitle;
    @BindView(R.id.footer) TextView footer;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.simple_activity);
        ButterKnife.bind(this);
        // TODO Use fields...
    }
}
```
- 与缓慢的反射相比，Butter Knife使用编译时生成的代码来执行View的查找，因此不必担心注解的性能问题。调用bind来生成这些代码，你可以查看或调试这些代码。比如上面的例子，生成的代码大致如下：

``` java
public void bind(ExampleActivity activity) {
    activity.subtitle = (android.widget.TextView) activity.findViewById(2130968578);
    activity.footer = (android.widget.TextView) activity.findViewById(2130968579);
    activity.title = (android.widget.TextView) activity.findViewById(2130968577);
}
```
### 基本原理
- ButterKnife使用了java Annotation Processing技术，就是在java代码编译成java字节码的时候就已经处理了@Bind、@OnClick等等注解，Annotation processing 是javac中用于编译时扫描和解析java注解的工具。它在编译阶段执行，它读入java源代码，解析注解，然后生成新的java代码。新生成的代码最后被编译成java字节码。注解解析器（Annotation Processor）不能改变读入的java 类，比如不能加入或者删除java方法。

### 工作流程
- 在编译Android工程时，ButterKnife工程中ButterKnifeProcessor类的process()方法会执行以下操作：
>1. 开始它会扫描Java代码中所有的ButterKnife注解 @Bind 、 @OnClick 、 @OnItemClicked 等。
>2. 当它发现一个类中含有任何一个注解时， ButterKnifeProcessor 会帮你生成一个Java类，名字类似 <className>$$ViewBinder ，这个新生成的类实现了 ViewBinder<T> 接口
>3. 这个 ViewBinder 类中包含了所有对应的代码，比如 @Bind 注解对应 findViewById() , @OnClick 对应了 view.setOnClickListener() 等等。
>4. 最后当Activity启动 ButterKnife.bind(this) 执行时，ButterKnife会去加载对应的 ViewBinder 类调用它们的 bind() 方法。


## 使用
### 资源绑定
- 资源绑定到类成员上可以使用@BindBool、@BindColor、@BindDimen、@BindDrawable、@BindInt、@BindString。使用时对应的注解需要传入对应的id资源。

``` java
class ExampleActivity extends Activity {
  @BindString(R.string.title) String title;
  @BindDrawable(R.drawable.graphic) Drawable graphic;
  @BindColor(R.color.red) int red; // int or ColorStateList field
  @BindDimen(R.dimen.spacer) Float spacer; // int (for pixel size) or float (for exact value) field
  // ...
}
```

### 在非Activity中使用绑定
- Butter Knife提供了bind的几个重载方法，只要传入view布局，便可以在任何对象中使用注解绑定，如下：

``` java
public class FancyFragment extends Fragment {
    @BindView(R.id.button1) Button button1;
    @BindView(R.id.button2) Button button2;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fancy_fragment, container, false);
        ButterKnife.bind(this, view);
        // TODO Use fields...
        return view;
    }
}
```
- 还有一种常见的场景，是在ListView的Adapter中，我们常常会使用ViewHolder：

``` java
public class MyAdapter extends BaseAdapter {
    @Override public View getView(int position, View view, ViewGroup parent) {
        ViewHolder holder;
        if (view != null) {
            holder = (ViewHolder) view.getTag();
        } else {
            view = inflater.inflate(R.layout.whatever, parent, false);
            holder = new ViewHolder(view);
            view.setTag(holder);
        }

        holder.name.setText("John Doe");
        // etc...

        return view;
    }

    static class ViewHolder {
        @BindView(R.id.title)
        TextView name;
        @BindView(R.id.job_title) TextView jobTitle;

        public ViewHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }
}
```
- 从上面可以看出，ButterKnife.bind的调用可以被放在任何你想调用findViewById的地方。

### 其他API
- 使用Activity作为根布局在任意对象中进行绑定，如果你使用了类似MVC的编程模式，你可以对controller使用它的Activity用ButterKnife.bind(this, activity)进行绑定。
- 使用ButterKnife.bind(this)绑定一个布局的子布局。如果你在布局中使用了<merge>标签并且在自定义的控件构造时inflate这个布局，你可以在inflate之后立即调用它。或者，你可以在onFinishInflate()回调中使用它。

### View 列表
- 可以一次性将多个Views绑定到一个List或者数组中：

``` java
@BindViews({ R.id.first_name, R.id.middle_name, R.id.last_name })
List<EditText> nameViews;
```
- apply 函数，该函数一次性在列表中的所有View上执行一个动作：

``` java
ButterKnife.apply(nameViews, DISABLE);
ButterKnife.apply(nameViews, ENABLED, false);
```
- Action和Setter接口能够让你指定一些简单的动作：

``` java
static final ButterKnife.Action<View> DISABLE = new ButterKnife.Action<View>() {
    @Override public void apply(View view, int index) {
        view.setEnabled(false);
    }
};
static final ButterKnife.Setter<View, Boolean> ENABLED = new ButterKnife.Setter<View, Boolean>() {
    @Override public void set(View view, Boolean value, int index) {
        view.setEnabled(value);
    }
};
```
- Android中的Property属性也可以使用apply方法进行设置：

``` java
ButterKnife.apply(nameViews, View.ALPHA, 0.0f);
```

### 监听器绑定
- 使用本框架，监听器能够自动的绑定到特定的执行方法上：

``` java
@OnClick(R.id.submit)
public void submit(View view) {
  // TODO submit data to server...
}
```
- 而监听器方法的参数都是可选的：

``` java
@OnClick(R.id.submit)
public void submit() {
    // TODO submit data to server...
}
```
- 指定一个特定的类型，Butter Knife也能识别：

``` java
@OnClick(R.id.submit)
public void sayHi(Button button) {
    button.setText("Hello!");
}
```

- 可以指定多个View ID到一个方法上，这样，这个方法就成为了这些View的共同事件处理。

``` java
@OnClick({ R.id.door1, R.id.door2, R.id.door3 })
public void pickDoor(DoorView door) {
    if (door.hasPrizeBehind()) {
        Toast.makeText(this, "You win!", LENGTH_SHORT).show();
    } else {
        Toast.makeText(this, "Try again", LENGTH_SHORT).show();
    }
}
```
- 自定义View时，绑定事件监听不需要指定ID

``` java
public class FancyButton extends Button {
    @OnClick
    public void onClick() {
        // TODO do something!
    }
}
```
### 重置绑定
- Fragment的生命周期与Activity不同。在Fragment中，如果你在onCreateView中使用绑定，那么你需要在onDestroyView中设置所有view为null。为此，ButterKnife返回一个Unbinder实例以便于你进行这项处理。在合适的生命周期回调中调用unbind函数就可完成重置。

``` java
public class FancyFragment extends Fragment {
    @BindView(R.id.button1) Button button1;
    @BindView(R.id.button2) Button button2;
    private Unbinder unbinder;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fancy_fragment, container, false);
        unbinder = ButterKnife.bind(this, view);
        // TODO Use fields...
        return view;
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
```

### 可选绑定
- 在默认情况下， @bind和监听器的绑定都是必须的，如果目标view没有找到的话，Butter Knife将会抛出个异常。
- 如果你并不想使用这样的默认行为而是想创建一个可选的绑定，那么你只需要在变量上使用@Nullable注解或在函数上使用@Option注解。

``` java
@Nullable @BindView(R.id.might_not_be_there) TextView mightNotBeThere;

@Optional @OnClick(R.id.maybe_missing) void onMaybeMissingClicked() {
    // TODO ...
}
```

### 对于包含多个方法的监听
- 当一个监听器包含多个回调函数时，使用函数的注解能够对其中任何一个函数进行绑定。每一个注解都会绑定到一个默认的回调。你也可以使用callback参数来指定一个其他函数作为回调。

``` java
@OnItemSelected(R.id.list_view)
void onItemSelected(int position) {
    // TODO ...
}

@OnItemSelected(value = R.id.maybe_missing, callback = NOTHING_SELECTED)
void onNothingSelected() {
    // TODO ...
}
```
### 福利
- Butter Knife提供了一个findViewById的简化代码：findById，用这个方法可以在View、Activity和Dialog中找到想要View，而且，该方法使用的泛型来对返回值进行转换，也就是说，你可以省去findViewById前面的强制转换了。

``` java
View view = LayoutInflater.from(context).inflate(R.layout.thing, null);
TextView firstName = ButterKnife.findById(view, R.id.first_name);
TextView lastName = ButterKnife.findById(view, R.id.last_name);
ImageView photo = ButterKnife.findById(view, R.id.photo);
```

## 总结
### ButterKnife的优势
- 强大的View绑定和Click事件处理功能，简化代码，提升开发效率。
- 方便的处理Adapter里的ViewHolder绑定问题。
- 运行时不会影响App效率，使用配置方便。
- 代码清晰，可读性强。

### 使用注意事项
- Activity ButterKnife.bind(this);必须在setContentView();之后，且父类bind绑定后，子类不需要再bind。
- 属性布局不能用private or static 修饰，否则会报错。
- setContentView()不能通过注解实现。
- 在Fragment生命周期中，onDestoryView也需要Butterknife.unbind(this)。
- ButterKnife不能在library module中使用（因为library中的R不是final类型的）。不过从8.4版本貌似支持了library的使用。






































++
