---
title: Android中View的事件分发
date: 2017-05-26 09:43:10
tags: Android view， touch，事件分发
categories: Android技术
---

## 前言
- 之前研究过View分发事件的源码，但是一段时间之后再来看这个知识点，发现都已经忘光光了，还得从头开始去查资料、学习。总结来说造成这种现象的原因有两个：
>1. 对View事件分发没有理解的很透彻。
>2. 技术是一种你不用，转瞬即逝的东西。

- 所以针对这种情况，我觉得也有两点对应的解决办法：
>1. 彻底的对这个知识点进行理解，然后写到自己的博客中，方便之后查阅。
>2. 要多写代码，自己去找一些好的项目做。多用才能印象深刻。

- 另外事件分发机制在面试的时候会经常被提及，掌握它可以给你加分不少。

## MotionEvent事件初探

- 我们对屏幕的点击、滑动、抬起等一些列的动作都是由一个个MotionEvent对象组成的，根据不同的动作，主要有以下三种类型：
>1. ACTION_DOWN: 手指刚接触屏幕，按下去的那一瞬间产生该事件。
>2. ACTION_MOVE: 手指在屏幕上移动的时候产生该事件。
>3. ACTION_UP: 手指从屏幕上松开的瞬间产生该事件。

- 上面是我们非常熟悉的三种事件， 从ACTION_DOWN开始到ACTION_UP结束称为一个事件序列。
- 正常情况下，MotionEvent有两种类型的操作：
>1. 单击：ACTION_DOWN  ------>  ACTION_UP
>2. 点击后滑动，再抬起： ACTION_DOWN   ----------> ACTION_MOVE  -------------> ACTION_MOVE ------------> ACTION_UP

- 此外在MotionEvent中还封装了一些其他信息，比如点击的坐标等。


## MotionEvent事件分发

- 下面三个方法也是我们非常熟悉的，View的事件分发由这三个方法完成：

### public boolean dispatchTouchEvent(MotionEvent event)
- 顾名思义，这个方法是进行事件分发的，如果一个MotionEvent传递给一个View，那么dispatchTouchEvent方法一定会被调用！
- 返回值：表示是否消费了当前事件。可能是当前view本身的onTouchEvent方法消费，也可能是子View（当前View是ViewGroup）的dispatchTouchEvent方法消费，返回true表示事件被消费，本次事件终止，返回false表示View以及子View均没有消费事件，将调用父View的onTouchEvent方法。

### public boolean onInterceptTouchEvent(MotionEvent ev)
- 当一个ViewGroup在接到MotionEvent事件序列时，首先会调用此方法判断是否需要拦截（View没有拦截方法，只有ViewGroup有）
- 返回值：返回true表示拦截事件，那么事件不继续传递，而直接调用ViewGroup的onTouchEvent方法，返回false表示不拦截，那么事件就传递给子View的dispatchTouchEvent方法。

### public boolean onTouchEvent(MotionEvent ev)
- 这个方法真正对MotionEvent进行消费和处理，它在dispatchTouchEvent方法中被调用。
- 返回值：返回True表示事件被消费，本次的事件终止，返回false表示事件没有被消费，将调用父View的OnTouchEvent方法。

### View && ViewGroup事件分发的不同
- ViewGroup本身也是View（ViewGroup继承自View），它采用了组合模式，目的是为了方便将View和ViewGroup能统一起来进行操作，方便操作。
- View本身不存在分发，所以也没有拦截方法（onInterceptTouchEvent），它只能在onTouchEvent方法中进行处理消费或者不消费。
- 用一张图来看下事件传递流程：
![ViewGroup事件传递](/upload/image/zlw/viewgroup_dispatchTouchEvent.PNG)

### 需要强调的点
- 子view可以通过requestDisallowInterceptTouchEvent方法干预父View的事件分发过程（ACTION_DOWN事件除外）而这就是我们处理滑动冲突常用的关键方法。
- 对于View&ViewGroup而言，如果设置了onTouchListener，那么onTouchListener方法中的onTouch会被回调，onTouch方法返回true，则onTouchEvent方法不会被调用（onClick事件是在onTouchEvent中调用）所以三者的优先级是：onTouch > onTouchEvent > onClick
- View的onTouchEvent方法默认会消费掉事件（返回true），除非view是不可点击的（clickable和longClickable同时为false），View的longClickable默认为false，如Button的clickable默认为true，而TextView的clickable默认为false。

## View事件分发源码
- 之前每次看事件分发这个知识点，都会看源码，源码这玩意，漫无目的的看，一会就蒙圈了，所以之前每次看，要花很长事件才能理解个大概，但是一段时间之后就又忘完了。。
- 下面的代码是参考网上某大神的讲解，只有关键代码，大家感兴趣的可以扒出来完整的代码对比查看。

### Activity的dispatchTouchEvent方法
- 当我们手触摸手机屏幕的时候，最先获取事件的肯定是硬件，系统有一个线程在循环收集屏幕硬件信息，当用户触摸屏幕时，该线程会把从硬件设备收集到的信息封装称为一个MotionEvent对象，然后将该对象放到一个队列中，系统的另一个线程循环读取消息队列中的MotionEvent，然后交给WMS去派发，WMS把该事件派发给当前处于栈顶的ACtivity。
![activity_dispatch.png](/upload/image/zlw/activity_dispatch.png)

- 由上面可知，点击事件会给Activity附属的Window进行分发，如果事件被消耗，那么返回true，如果返回false，表示事件分发下去没有View可以处理，则最后返回到Activity自己的onTouchEvent方法执行。
- 这里的Window其实是一个PhoneWindow，它继承自Window，里面包含DecorView是Android一个界面最顶层的View。
![phoneWindow.png](/upload/image/zlw/phoneWindow.png)

### ViewGroup的事件传递—onInterceptTouchEvent
- DecorView继承自FrameLayout，很显然是一个ViewGroup，之前提到过：事件到达View会调用dispatchTouchEvent方法，同时ViewGroup会先判断是否拦截该事件。
![viewGroup_dispatch.png](/upload/image/zlw/viewGroup_dispatch.png)

- 上面提到过，子View可以通过requestDisallowInterceptTouchEvent方法干预父View的事件分发过程（ACTION_DOWN事件除外），从上面的代码我们能够找到原因，是因为当时ACTION_DOWN事件时，系统会重置ALLOW_INTERCEPT标志位并且将mFirstTouchTarget（事件由子view去处理时mFirstTouchTarget会被赋值并指向子view）设置为null。
- 当事件为ACTION_DOWN 或者mFirstTouchTarget != null(即事件由子View处理)时会进行拦截判断，判断依据是子view是否调用了requestDisallowInterceptTouchEvent方法。
- 如果事件不为ACTION_DOWN且mFirstTouchTarget != null(即事件由ViewGroup自己处理)那么事件本来是自己处理也没必要拦截了。
- 所以onInterceptTouchEvent并非每次都会调用，如果要处理所有的点击事件，需要用dispatchTouchEvent方法。

### ViewGroup的事件传递—子View处理
- 当ViewGroup不拦截事件，那么事件交给子View处理

``` file
final View[] children = mChildren;
// 对所有的子view进行遍历
for (int i = childrenCount - 1; i >= 0; i--) {
    final int childIndex = customOrder
            ? getChildDrawingOrder(childrenCount, i) : i;
    final View child = (preorderedList == null)
            ? children[childIndex] : preorderedList.get(childIndex);

    // If there is a view that has accessibility focus we want it
    // to get the event first and if not handled we will perform a
    // normal dispatch. We may do a double iteration but this is
    // safer given the timeframe.
    if (childWithAccessibilityFocus != null) {
        if (childWithAccessibilityFocus != child) {
            continue;
        }
        childWithAccessibilityFocus = null;
        i = childrenCount - 1;
    }

    // 1. 这里判断View可见且没有播放动画。 2. 点击事件的坐标落在View的范围内
    // 如果上述两个条件一个不满足则continue继续循环下一个view。
    if (!canViewReceivePointerEvents(child)
            || !isTransformedTouchPointInView(x, y, child, null)) {
        ev.setTargetAccessibilityFocus(false);
        continue;
    }

    newTouchTarget = getTouchTarget(child);
    // 如果已经有子view在处理了，即newTouchTarget不为null，则跳出循环
    // 正常情况下，这里的newTouchTarget是null的。
    if (newTouchTarget != null) {
        // Child is already receiving touch within its bounds.
        // Give it the new pointer in addition to the ones it is handling.
        newTouchTarget.pointerIdBits |= idBitsToAssign;
        break;
    }

    resetCancelNextUpFlag(child);
    // dispatchTransformedTouchEvent第三个参数child不为null
    // 实际调用的是child的dispatchTouchEvent方法
    if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
        // Child wants to receive touch within its bounds.
        mLastTouchDownTime = ev.getDownTime();
        if (preorderedList != null) {
            // childIndex points into presorted list, find original index
            for (int j = 0; j < childrenCount; j++) {
                if (children[childIndex] == mChildren[j]) {
                    mLastTouchDownIndex = j;
                    break;
                }
            }
        } else {
            mLastTouchDownIndex = childIndex;
        }
        mLastTouchDownX = ev.getX();
        mLastTouchDownY = ev.getY();
        // 这里对mFirstTouchTarget进行赋值
        newTouchTarget = addTouchTarget(child, idBitsToAssign);
        alreadyDispatchedToNewTouchTarget = true;
        // 将事件分发给子view，然后跳出了for循环
        break;
    }

    // The accessibility focus didn't handle the event, so clear
    // the flag and do a normal dispatch to all children.
    ev.setTargetAccessibilityFocus(false);
}
```

- 如果没有找到合适的子view去处理事件，即ViewGroup并没有子view或者子view虽然处理了事件，但是子view的dispatchTouchEvent返回了false（一般是子view的onTouchEvent返回false），那么ViewGroup自己处理这个事件。如下代码所示：

``` file
            // Dispatch to touch targets.
           if (mFirstTouchTarget == null) {
               // No touch targets so treat this as an ordinary view.
               // 这里第三个参数child是null，区别于上面第三个参数传递的是view
               handled = dispatchTransformedTouchEvent(ev, canceled, null,
                       TouchTarget.ALL_POINTER_IDS);
           }

           // dispatchTransformedTouchEvent 中的代码
           if (child == null) {
               handled = super.dispatchTouchEvent(event);
           } else {
               handled = child.dispatchTouchEvent(event);
           }
```
- 当child为null时，handled = super.dispatchTouchEvent(event); 即调用了View的dispatchTouchEvent方法，点击事件给了View，此致事件分发全部结束。

## view.dispatchTouchEvent
- 从上可知，如果没有子View处理点击事件，最终会调用View.dispatchTouchEvent。下面是它的源码，我将关键点都写到注释中。

``` file
public boolean dispatchTouchEvent(MotionEvent event) {
        // If the event should be handled by accessibility focus first.
        // 用于Android辅助功能，可忽略
        if (event.isTargetAccessibilityFocus()) {
            // We don't have focus or no virtual descendant has it, do not handle the event.
            if (!isAccessibilityFocusedViewOrHost()) {
                return false;
            }
            // We have focus and got the event, then use normal event dispatch.
            event.setTargetAccessibilityFocus(false);
        }

        boolean result = false;

        if (mInputEventConsistencyVerifier != null) {
            mInputEventConsistencyVerifier.onTouchEvent(event, 0);
        }

        final int actionMasked = event.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            // Defensive cleanup for new gesture
            stopNestedScroll();
        }

        // 如果窗口没有被遮盖
        if (onFilterTouchEventForSecurity(event)) {
            //noinspection SimplifiableIfStatement
            // 当前监听事件
            ListenerInfo li = mListenerInfo;
            if (li != null && li.mOnTouchListener != null
                    && (mViewFlags & ENABLED_MASK) == ENABLED
                    && li.mOnTouchListener.onTouch(this, event)) {
                result = true;
            }

            // result 为false时，调用自己的onTouchEvent方法处理
            if (!result && onTouchEvent(event)) {
                result = true;
            }
        }

        if (!result && mInputEventConsistencyVerifier != null) {
            mInputEventConsistencyVerifier.onUnhandledEvent(event, 0);
        }

        // Clean up after nested scrolls if this is the end of a gesture;
        // also cancel it if we tried an ACTION_DOWN but we didn't want the rest
        // of the gesture.
        if (actionMasked == MotionEvent.ACTION_UP ||
                actionMasked == MotionEvent.ACTION_CANCEL ||
                (actionMasked == MotionEvent.ACTION_DOWN && !result)) {
            stopNestedScroll();
        }

        return result;
    }

```
- 从上面代码可以看出，View会先判断是否设置了OnTouchListener，如果设置了OnTouchListener并且onTouch方法返回了true，那么onTouchEvent不会被调用，当没有设置OnTouchListener或者设置了OnTouchListener但是onTouch方法返回false则会调用View自己的onTouchEvent方法。

## view的onTouchEvent方法
- 直接来看代码吧

``` java
public boolean onTouchEvent(MotionEvent event) {
    final float x = event.getX();
    final float y = event.getY();
    final int viewFlags = mViewFlags;

    //如果当前View是Disabled状态且是可点击则会消费掉事件（return true)
    if ((viewFlags & ENABLED_MASK) == DISABLED) {
        if (event.getAction() == MotionEvent.ACTION_UP && (mPrivateFlags & PFLAG_PRESSED) != 0) {
            setPressed(false);
        }
        // A disabled view that is clickable still consumes the touch
        // events, it just doesn't respond to them.
        return (((viewFlags & CLICKABLE) == CLICKABLE ||
                (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE));
    }

    //如果设置了mTouchDelegate，则会将事件交给代理者处理，直接return true，
    // 如果大家希望自己的View增加它的touch范围，可以尝试使用TouchDelegate
    if (mTouchDelegate != null) {
        if (mTouchDelegate.onTouchEvent(event)) {
            return true;
        }
    }
    //如果我们的View可以点击或者可以长按，则，注意IF的范围，最终一定return true
    if (((viewFlags & CLICKABLE) == CLICKABLE ||
            (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                boolean prepressed = (mPrivateFlags & PFLAG_PREPRESSED) != 0;
                if ((mPrivateFlags & PFLAG_PRESSED) != 0 || prepressed) {
                    // take focus if we don't have it already and we should in
                    // touch mode.
                    boolean focusTaken = false;
                    if (isFocusable() && isFocusableInTouchMode() && !isFocused()) {
                        focusTaken = requestFocus();
                    }

                    if (prepressed) {
                        // The button is being released before we actually
                        // showed it as pressed.  Make it show the pressed
                        // state now (before scheduling the click) to ensure
                        // the user sees it.
                        setPressed(true, x, y);
                   }

                    if (!mHasPerformedLongPress) {
                        // This is a tap, so remove the longpress check
                        removeLongPressCallback();

                        // Only perform take click actions if we were in the pressed state
                        if (!focusTaken) {
                            // Use a Runnable and post this rather than calling
                            // performClick directly. This lets other visual state
                            // of the view update before click actions start.
                            if (mPerformClick == null) {
                                mPerformClick = new PerformClick();
                            }
                            if (!post(mPerformClick)) {
                                performClick();
                            }
                        }
                    }

                    if (mUnsetPressedState == null) {
                        mUnsetPressedState = new UnsetPressedState();
                    }

                    if (prepressed) {
                        postDelayed(mUnsetPressedState,
                                ViewConfiguration.getPressedStateDuration());
                    } else if (!post(mUnsetPressedState)) {
                        // If the post failed, unpress right now
                        mUnsetPressedState.run();
                    }

                    removeTapCallback();
                }
                break;

            case MotionEvent.ACTION_DOWN:
                mHasPerformedLongPress = false;

                if (performButtonActionOnTouchDown(event)) {
                    break;
                }

                // Walk up the hierarchy to determine if we're inside a scrolling container.
                boolean isInScrollingContainer = isInScrollingContainer();

                // For views inside a scrolling container, delay the pressed feedback for
                // a short period in case this is a scroll.
                if (isInScrollingContainer) {
                    mPrivateFlags |= PFLAG_PREPRESSED;
                    if (mPendingCheckForTap == null) {
                        mPendingCheckForTap = new CheckForTap();
                    }
                    mPendingCheckForTap.x = event.getX();
                    mPendingCheckForTap.y = event.getY();
                    postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                } else {
                    // Not inside a scrolling container, so show the feedback right away
                    setPressed(true, x, y);
                    checkForLongClick(0);
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                removeTapCallback();
                removeLongPressCallback();
                break;

            case MotionEvent.ACTION_MOVE:
                drawableHotspotChanged(x, y);

                // Be lenient about moving outside of buttons
                if (!pointInView(x, y, mTouchSlop)) {
                    // Outside button
                    removeTapCallback();
                    if ((mPrivateFlags & PFLAG_PRESSED) != 0) {
                        // Remove any future long press/tap checks
                        removeLongPressCallback();

                        setPressed(false);
                    }
                }
                break;
        }

        return true;
    }

    return false;
}
```
- 从上面代码可知，即便View是disable状态，依然不会影响事件的消费，只是它看起来不可用。
- 另外只要CLICKABLE和LONG_CLICKABLE有一个为true，就一定会消费这个事件（我们上面提到过这个点）。即View的onTouchEvent方法默认都会消费掉事件（返回true），除非它是不可点击的。
- ACTION_UP方法中有performClick()，如果View设置了OnClickListener，那么会回调onClick方法。
- 最后强调一点，假如我们想让View默认不可点击，将View的Clickable设置为false，在合适的时候需要可点击所以我们又给View设置了OnClickListener，那么你发现View依然可以点击，也就是说setClickable失效了，这是因为在setOnClickListener的时候，系统会覆盖点击状态，将View的点击状态设置为true，所以应该在setOnclickListener之后SetClickable()。

## 总结
- 上面比较系统的讲了MotionEvent事件分发机制，可能一次看完有点吃不消，需要多次查看来逐步消化。































##
