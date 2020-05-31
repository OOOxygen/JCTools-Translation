### JCTools 源码译注（源码剖析）
使用原仓库的master分支进行译注，[原仓库传送门](https://github.com/JCTools/JCTools)。  
初步扫了一遍，并发Map的复杂度已经超出了我的理解能力，因此该项目只会翻译(注释)Queue的实现，Channel也可能会翻译。  

Q: 为什么不使用某个固定版本？  
A: 最初采用**V3.0.0**版本，但是在阅读的过程中，发现一些bug，在上报之后，作者进行了一些修改，导致差异变大，因此决定还是使用master进行译注。

PS: 译注过程中会尽量保证增量为绿色。

#### 进度
目前完成常见队列的译注，包括:  
1. 基于数组的队列： MpscArrayQueue、MpmcArrayQueue、SpscArrayQueue、SpmcArrayQueue  
2. 基于链表的队列： MpscLinkedQueue、SpscLinkedQueue  
3. 基于**LinkedArray**的队列（手动点赞）： SpscChunkedArrayQueue、SpscGrowableArrayQueue、SpscUnboundedArrayQueue、
MpscGrowableArrayQueue、MpscChunkedArrayQueue、MpscUnboundedArrayQueue  
4. MpUnboundedXaddArrayQueue（手动点赞）：
5. 其它类型队列：MpscCompoundQueue

PS: 本次翻译（注释）还是比较匆忙的，若有错误或有疑惑可以提出。

#### 仔细阅读文档，遵守接口和实现类的约束
如果不是消费者或生产者，则看队列的状态可能是不满足状态约束的（比如生产者进度和消费者进度之间的约束），因此不能充当生产者或消费者时，慎用队列中的方法。
（如果站在消费者或生产者的角度都能看见队列的状态不满足状态约束，那么必定有bug）


#### Bugs
就目前阅读的源码而言，有以下bug：
1. SpscLinkedQueue，SpscArrayQueue 生产者先使得元素对外可见，再使索引对外可见，而消费者在poll和peek时没有验证生产者进度和消费者进度的关系，
因此特定的时序下，消费者会看见自己的进度超过生产者，从而导致bug。  
典型就是isEmpty + poll/peek 先检查后执行的组合调用。  
这个组合在多消费者下，如果isEmpty为false，poll/peek当然是可以返回null的；  
但是在单消费者模式下，如果isEmpty为false，poll/peek是不应该返回null的，违背了接口约定。  
PS: 我上报之后，他们已经修复了该bug，预计会出现在下一版本。不过他们对SpscArrayQueue的修复和我想的不太一样（他们允许了超过进度，因此修正了isEmpty方法），不过似乎也是对的...  
[isEmpty约束 传送门](https://github.com/JCTools/JCTools/issues/292)  
[*ArrayQueue的size约束 传送门](https://github.com/JCTools/JCTools/issues/297)  

2. XXmcArrayQueue 基于环形数组的**多消费者**队列，在**peek**时，先读取消费者索引，再加载元素，这是复合操作，这期间队列的状态可能发生改变，
因此可能peek到一个新填充的数据，而不是下一个要消费的数据，因此建议不要使用peek。  
它可以表示为另一个约束：**对于任何消费者而言，poll/relaxedPoll/peek/relaxedPeek都不应该表现为乱序（必须满足元素的插入顺序）。**  
[传送门](https://github.com/JCTools/JCTools/pull/295)

#### 读写内存方法约定
第一个字母表示 Load 或 Store, 第二个字母表示模式(方式)。  
模式分三种： v -> volatile, o -> ordered (J9 release)， p -> plain  
lv: loadVolatile  (在J9中规范为 Volatile Mode)
sv: storeVolatile

so: storeOrdered (在J9中规范为 Release Acquire Mode)

lp: loadPlain  (在J9中规范为 Plain Mode)
sp: storePlain  

J9中对数据读写模式进行了规范，建议大家看看**VarHandle**中的文档，有助于你理解JCTools中的设计。  
附上Doug Lea的对J9内存模型讲解的文章 [J9MM](http://gee.cs.oswego.edu/dl/html/j9mm.html)。

******

[![Total alerts](https://img.shields.io/lgtm/alerts/g/JCTools/JCTools.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/JCTools/JCTools/alerts/)
[![Coverage Status](https://coveralls.io/repos/github/JCTools/JCTools/badge.svg?branch=master)](https://coveralls.io/github/JCTools/JCTools?branch=master)
[![Build Status](https://travis-ci.org/JCTools/JCTools.svg?branch=master)](https://travis-ci.org/JCTools/JCTools)
[![Gitter](https://badges.gitter.im/JCTools/JCTools.svg)](https://gitter.im/JCTools/JCTools?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

JCTools
==========
Java Concurrency Tools for the JVM. This project aims to offer some concurrent data structures currently missing from
the JDK:
- SPSC/MPSC/SPMC/MPMC variations for concurrent queues:
  * SPSC - Single Producer Single Consumer (Wait Free, bounded and unbounded)
  * MPSC - Multi Producer Single Consumer (Lock less, bounded and unbounded)
  * SPMC - Single Producer Multi Consumer (Lock less, bounded)
  * MPMC - Multi Producer Multi Consumer (Lock less, bounded)
  
- SPSC/MPSC linked array queues offer a balance between performance, allocation and footprint

- An expanded queue interface (MessagePassingQueue):
  * relaxedOffer/Peek/Poll: trade off conflated guarantee on full/empty queue state with improved performance.
  * drain/fill: batch read and write methods for increased throughput and reduced contention
  
There's more to come and contributions/suggestions are most welcome. JCTools has enjoyed support from the community
and contributions in the form of issues/tests/documentation/code have helped it grow.
JCTools offers excellent performance at a reasonable price (FREE! under the Apache 2.0 License). It's stable and in
use by such distinguished frameworks as Netty, RxJava and others. JCTools is also used by commercial products to great result.

Get it NOW!
==========
Add the latest version as a dependency using Maven:
```xml
        <dependency>
            <groupId>org.jctools</groupId>
            <artifactId>jctools-core</artifactId>
            <version>3.0.0</version>
        </dependency>
```

Or use the awesome, built from source, <https://jitpack.io/> version, you'll need to add the Jitpack repository:
```xml
        <repository>
          <id>jitpack.io</id>
           <url>https://jitpack.io</url>
        </repository>
```

And setup the following dependency:
```xml
        <dependency>
            <groupId>com.github.JCTools.JCTools</groupId>
            <artifactId>jctools-core</artifactId>
            <version>v3.0.0</version>
        </dependency>
```

You can also depend on latest snapshot from this repository (live on the edge) by setting the version to '3.0.1-SNAPSHOT'.


Build it from source
==========
JCTools is maven built and requires an existing Maven installation and JDK8 (only for building, runtime is 1.6 compliant).

With 'MAVEN_HOME/bin' on the path and JDK8 set to your 'JAVA_HOME' you should be able to run "mvn install" from this
directory.


But I have a zero-dependency/single-jar project
==========
While you are free to copy & extend JCTools, we would much prefer it if you have a versioned dependency on JCTools to
enable better support, upgrade paths and discussion. The shade plugin for Maven/Gradle is the preferred way to get
JCTools fused with your library. Examples are available in the [ShadeJCToolsSamples](https://github.com/JCTools/ShadeJCToolsSamples) project.


Benchmarks
==========
JCTools is benchmarked using both JMH benchmarks and handrolled harnesses. The benchmarks and related instructions can be
found in the jctools-benchmarks module [README](jctools-benchmarks/README.md). Go wild and please let us know how it did on your hardware.

Concurrency Testing
===========
```
mvn package
cd jctools-concurrency-test
java -jar target/concurrency-test.jar -v
```
Come up to the lab...
==========
Experimental work is available under the jctools-experimental module. Most of the stuff is developed with an eye to
eventually porting it to the core where it will be stabilized and released, but some implementations are kept purely for reference and some may never graduate. Beware the Jabberwock my child.

Have Questions? Suggestions?
==========
The best way to discuss JCTools is on the GitHub issues system. Any question is good, and GitHub provides a better
platform for knowledge sharing than twitter/mailing-list/gitter (or at least that's what we think).

Thanks!!!
=====
We have kindly been awarded [IntelliJ IDEA](https://www.jetbrains.com/idea/) licences by [JetBrains](https://www.jetbrains.com/) to aid in the development of JCTools. It's a great suite of tools which has benefited the developers and ultimately the community.

It's an awesome and inspiring company, [**BUY THEIR PRODUCTS NOW!!!**](https://www.jetbrains.com/store/#edition=commercial)

JCTools has enjoyed a steady stream of PRs, suggestions and user feedback. It's a community! Thank you all for getting involved!
