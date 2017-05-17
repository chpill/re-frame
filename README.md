
<img src="/images/logo/re-frankenstein-logo.png?raw=true">

# Re-Frankenstein

This is a fork of [re-frame](https://github.com/Day8/re-frame/) to experiment
with local state and [rum]. Apart from the subscription mechanism, re-frame is
not at all tied to reagent. And the heart of the interceptor code is not
dependent on global mutable state. This fork illustrates how, with a few tweaks
to the core namespaces, we can actually use most of re-frame without using the
global `re-frame.db/app-db` atom. It is designed to be backward compatible with
re-frame. The API of re-frame is still there, untouched (well, almost!).


I'd be very happy if you could try to use it from clojars and tell me if
anything breaks in your re-frame app! But this is very experimental software, I
will make no guarantees about stability of the API here.

[![Clojars Project](https://img.shields.io/clojars/v/com.chpill.re-frankenstein.svg)](https://clojars.org/com.chpill.re-frankenstein)


[rum]: https://github.com/tonsky/rum

## TL;DR

Just look at the todomvc example extended with the new "frankenstein"
todos [here](https://chpill.github.io/todos-re-frankenstein/)!. The todos at the
top are the original, **unmodified** re-frame todos. Below that, you will see
another todos list, that shares the behavior of the original, but uses its own
local state. You can even spawn more of those todos, each with its own state! Of
course, there is still only one local storage, so this is a *last write win*
situation here. There are also some issues you may notice about the routing part
of the app.


Go through its [implemention], you will see that the `db` and `events` namespaces
were not modified. You will also see that we use a different set of views, using
[rum], and subscription, using [derivatives]. In the core namespace, we create
some local-state. Let's call him Frank.

[derivatives]: https://github.com/martinklepsch/derivatives

[implementation]: https://github.com/chpill/re-frankenstein/tree/6353a6a8bd93a88ad2f72e121b41fe53b6b48064/examples/todomvc/src/todomvc

## Basics

Create a re-frame app, and register effect and coeffect handlers as you would
normally do. The only restriction here is that you may not reference the
`re-frame.db/app-state` in those handlers. Same restriction if you want to write
custom interceptors.

Then you can create a new abomination (you may create as many as you like).

```clj
(def fresh-monster (re-frame.frank/create))

(frank/dispatch! fresh-monster [:an-event])
```

Congratulations, you've just made a transaction on some local state!


## Re-Frame, Rum and Derivatives: The unholy stitching together

The [re-frame.rum] namespace provides rum mixins to interact with the react
context, so that we can inject our monster into it and access it from our rum
views. To replace the original subscription mechanism, we use the [derivatives]
library. We just provide it our monster (that implements some `Atom`
capabilities) and it does all the hard work of making the magic of reactive
programming happen.

[re-frame.rum]: https://github.com/chpill/re-frankenstein/blob/6353a6a8bd93a88ad2f72e121b41fe53b6b48064/src/re_frame/rum.cljs

So, yeah, 3 different libraries mashed into the picture... Hence the reference
to the creature of the [Dr Stein] (It is highly recommended that you play that
song in another tab while you continue reading this. You know you need more
power metal in your life.)

Someone suggested me that a more flattering comparison would have been the power
rangers assembling into the Megazord. Although it would have been nice to have
`morphing-time!` somewhere in the API, Helloween has yet to make a song about
them.

[Dr Stein]: https://www.youtube.com/watch?v=3FFTQRmsK0k


To see a simple case of "stitching", look at the extented [simple clock example].

The root component is kind of complicated in the way it is declared (because the
mixin code has to be given functions to extract the different pieces from the
arguments). Not really happy with that -- hopefully we'll find a better way.

[simple clock example]: https://github.com/chpill/re-frankenstein/blob/6353a6a8bd93a88ad2f72e121b41fe53b6b48064/examples/simple/src/simple/core.cljs


## How does the local part works?

Every time you register an event handler using [reg-event-db, reg-event-fx or
reg-event-ctx], notice that you are in fact registering a chain of interceptor.
`cofx/inject-db` and `fx/do-fx` are always the first two interceptors of the
chain. That is no good for us, because they both indirectly reference the global
mutable `app-db`.

Fortunately, interceptors are represented as data! So when we
create a new monster (using `(def fresh-monster (re-frame.frank/create))`), we
in fact de-reference the value of the handler registry, and we modify that value
to replace the parts that are "global stateful" with some "local stateful" ones.
[See for yourself].

[reg-event-db, reg-event-fx or reg-event-ctx]: https://github.com/Day8/re-frame/blob/cf61b2db1da360687c6b247888b29fb58bedf7cb/src/re_frame/core.cljc#L77-L103

[See for yourself]: https://github.com/chpill/re-frankenstein/blob/6353a6a8bd93a88ad2f72e121b41fe53b6b48064/src/re_frame/frank.cljs#L86


### Is this a joke?

Well it may have started that way, but it actually works so...

More seriously, Mike Thompson proposed the term `frame` in this [issue about
global state]. So if there is some interest in merging some of this work in
re-frame, this would probably be the official name for this thing.

[issue about global state]: https://github.com/Day8/re-frame/issues/137

## Goals

Here are some of the directions I would like to explore with this fork, in no
particular order:

- Find a less ugly way to stitch together the rum root component

- Warn the user when some of the context mixins are used without the rum `defcs`
  macro (that allows to access the local state of the rum component). Using the
  classic `defc` macro will not give you the things you're trying to
  access from the context, and it can be hard to trace why.
  
- Find a way to use the subscription mechanism without reagent? I have honestly
  never used subscriptions, so I really don't know what I'm dealing with here. 

- Tracing? In the [issue about global state] mentioned above, it is said that
  the tracing system uses an atom internally. Again, I have no familiarity with
  what it is or what it does yet.

- cljc: The new namespaces introduced here are cljs. It would be better to have
  it work on the JVM too.

- [devcards] helpers: As the views only consume data and dispatch events from
  functions in the react context, it should be possible to test them in
  isolation with some devcards. We should at least provide an example, and if it
  is hard to do, maybe some helpers?
  
[devcards]: https://github.com/bhauman/devcards

- Server side rendering: Rum has first class support for server-side rendering,
  so we should make it easy to use it. I'm not sure this is the best place to deal
  with this issue, as the problem really boils down to how you get data to you
  views, aka the subscriptions or derivatives for now. I'll have to dig more
  about how to do it with [derivatives].



# v Original README below v

## Derived Values, Flowing

> This, milord, is my family's axe. We have owned it for almost nine hundred years, see. Of course,
sometimes it needed a new blade. And sometimes it has required a new handle, new designs on the
metalwork, a little refreshing of the ornamentation ... but is this not the nine hundred-year-old
axe of my family? And because it has changed gently over time, it is still a pretty good axe,
y'know. Pretty good.

> -- Terry Pratchett, The Fifth Elephant <br>
> &nbsp;&nbsp;&nbsp; reflecting on identity, flow and derived values


[![Clojars Project](https://img.shields.io/clojars/v/re-frame.svg)](https://clojars.org/re-frame)
[![GitHub license](https://img.shields.io/github/license/Day8/re-frame.svg)](license.txt)
[![Circle CI](https://circleci.com/gh/Day8/re-frame/tree/develop.svg?style=shield&circle-token=:circle-ci-badge-token)](https://circleci.com/gh/Day8/re-frame/tree/develop)
[![Circle CI](https://circleci.com/gh/Day8/re-frame/tree/master.svg?style=shield&circle-token=:circle-ci-badge-token)](https://circleci.com/gh/Day8/re-frame/tree/master)


## Why Should You Care?

Perhaps:

1.  You want to develop an [SPA] in ClojureScript, and you are looking for a framework.
2.  You believe Facebook did something magnificent when it created React, and
    you are curious about the further implications. Is the combination of
    `reactive programming`, `functional programming` and `immutable data` going to
    **completely change everything**?  And, if so, what would that look like in a language
    that embraces those paradigms?
3.  You're taking a [Functional Design and Programming course](http://www.eli.sdsu.edu/courses/fall15/cs696/index.html) at San Diego State University
    and you have a re-frame/reagent assignment due.  You've left the reading a bit late, right?
4.  You know Redux, Elm, Cycle.js or Pux and you're
    interested in a ClojureScript implementation.
    In this space, re-frame is very old, hopefully in a Gandalf kind of way.
    First designed in Dec 2014, it even slightly pre-dates the official Elm Architecture,
    although thankfully we were influenced by early-Elm concepts like `foldp` and `lift`, as well as 
    terrific Clojure projects like [Pedestal App], [Om] and [Hoplon]. Since then,
    re-frame has pioneered ideas like event handler middleware,
    coeffect accretion, and de-duplicated signal graphs.
5.  Which leads us to the most important point: **re-frame is impressively buzzword compliant**. It has reactivity,
    unidirectional data flow, pristinely pure functions,
    interceptors, coeffects, conveyor belts, statechart-friendliness (FSM)
    and claims an immaculate hammock conception. It also has a charming
    xkcd reference (soon) and a hilarious, insiders-joke T-shirt,
    ideal for conferences (in design).  What could possibly go wrong?

[OM]:https://github.com/swannodette/om
[Hoplon]:http://hoplon.io/
[Pedestal App]:https://github.com/pedestal/pedestal-app


## It Leverages Data

You might already know that ClojureScript is a modern lisp, and that
lisps are **homoiconic**.  If not, you do now.

That homoiconic bit is significant. It means you program in a lisp by creating and
assembling lisp data structures. Dwell on that for a moment. You are **programming in data**. 
The functions which later transform data, themselves start as data.

Clojure programmers place particular 
emphasis on the primacy of data. When they aren't re-watching Rich Hickey videos, 
and wishing their hair was darker and more curly, 
they meditate on aphorisms like **Data is the ultimate in late binding**.

I cannot stress enough what a big deal this is. It may seem 
like a syntax curiosity at first but, when the penny drops for 
you on this, it tends to be a profound moment. And once you 
understand the importance of this concept at the language level, 
you naturally want to leverage similar power at the library level.

So, it will come as no surprise, then, to know that re-frame has a 
data oriented design. Events are data. Effects are data. DOM is data.
The functions which transform data are registered and looked up via 
data. Interceptors (data) are preferred over middleware (higher 
order functions). Etc.

**Data - that's the way we roll.**


## re-frame

re-frame is a pattern for writing [SPAs] in ClojureScript, using [Reagent].

McCoy might report "It's MVC, Jim, but not as we know it".  And you would respond 
"McCoy, you trouble maker, why even mention an OO pattern? 
re-frame is a **functional framework**."

Being a functional framework, it is about data, and the functions 
which transform that data.

### It is a loop

Architecturally, re-frame implements "a perpetual loop".

To build an app, you hang pure functions on certain parts of this loop, 
and re-frame looks after the `conveyance of data` 
around the loop, into and out of the transforming functions you 
provide - hence a tag line of "Derived Values, Flowing".

### It does Physics

Remember this diagram from school? The water cycle, right?

<img height="350px" align="right" src="/images/the-water-cycle.png?raw=true">

Two distinct stages, involving water in different phases, being acted upon
by different forces: gravity working one way, evaporation/convection the other.

To understand re-frame, **imagine data flowing around that loop instead of water**.

re-frame
provides the conveyance of the data around the loop - the equivalent of gravity, evaporation and convection.
You design what's flowing and then you hang functions off the loop at
various points to compute the data's phase changes.

Sure, right now, you're thinking "lazy sod - make a proper Computer Science-y diagram". But, no.
Joe Armstrong says "don't break the laws of physics" - I'm sure
you've seen the videos - and if he says to do something, you do it
(unless Rich Hickey disagrees, and says to do something else). So,
this diagram, apart from being a plausible analogy which might help
you to understand re-frame, is **practically proof** it does physics.

## It is a 6-domino cascade

<img align="right" src="/images/Readme/Dominoes-small.jpg?raw=true">

Computationally, each iteration of the loop involves a
six domino cascade.

One domino triggers the next, which triggers the next, et cetera, until we are 
back at the beginning of the loop, whereupon the dominoes spring to attention 
again, ready for the next iteration of the same cascade.

The six dominoes are ...

### 1st Domino - Event Dispatch

An `event` is sent when something happens - the user 
clicks a button, or a websocket receives a new message.

Without the impulse of a triggering `event`, no six domino cascade occurs.
It is only because of `event`s that a re-frame app is propelled,
loop iteration after loop iteration, from one state to the next.

re-frame is `event` driven.

### 2nd Domino - Event Handling

In response to an `event`, an application must decide what action to take. 
This is known as `event handling`.

Event handler functions compute side effects (known in re-frame simply as `effects`). 
More accurately, they compute 
a **description of `effects`**. This description is a data structure 
which says, declaratively, how the world should change (because of the event).

Much of the time, only the "application state" of the SPA itself need
change, but sometimes the outside world must also be affected
(localstore, cookies, databases, emails, logs, etc).

### 3rd Domino - Effect Handling

The descriptions of `effects` are realised (actioned).

Now, to a functional programmer, `effects` are scary in a 
[xenomorph kind of way](https://www.google.com.au/search?q=xenomorph).
Nothing messes with functional purity
quite like the need for side effects. On the other hand, `effects` are 
marvelous because they move the app forward. Without them, 
an app stays stuck in one state forever, never achieving anything.

So re-frame embraces the protagonist nature of `effects` - the entire, unruly zoo of them - but
it does so in a controlled and largely hidden way, and in a manner which is debuggable, auditable, mockable and pluggable.

### We're Now At A Pivot Point

Domino 3 just changed the world and, very often, one particular part of it: the **application state**.

re-frame's `app state` is held in one place - think of it like you 
would an in-memory, central database for the app (details later).

Any changes to `app state` trigger the next part of the cascade 
involving dominoes 4-5-6.

### There's a Formula For It 

The 4-5-6 domino cascade implements the formula made famous by Facebook's ground-breaking React library:  
  `v = f(s)`

A view, `v`, is a function, `f`, of the app state, `s`.

Said another way, there are functions `f` that compute which DOM nodes, `v`,
should be displayed to the user when the application is in a given app state, `s`.

Or, to capture the dynamics we'd say: **over time**, as `s` changes, `f`
will be re-run each time to compute new `v`, forever keeping `v` up to date with the current `s`.

Or, with yet another emphasis: **over time** what is presented to the user changes in response to application state changes. 

In our case, domino 3 changes `s`, the application state,
and, in response, dominoes 4-5-6 are concerned with re-running `f` to compute the new `v` 
shown to the user.

Except, of course, there are nuances.  For instance, there's no single `f` to run.
There may be many functions which collectively build the overall DOM, 
and only part of `s` may change at any one time, so only part of the 
`v` (DOM) need be re-computed and updated. And some parts of `v` might not 
be showing right now.


### Domino 4 - Query

<img align="right" src="/images/Readme/6dominoes.png?raw=true">

Domino 4 is about extracting data from "app state", and providing it 
in the right format for view functions (which are Domino 5).

Domino 4 is a novel and efficient de-duplicated signal graph which 
runs query functions on the app state, `s`, efficiently computing 
reactive, multi-layered, "materialised views" of `s`.

(Relax about any unfamiliar terminology, you'll soon 
see how simple the code actually is)

### Domino 5 - View

Domino 5 is one or more **view functions** (aka Reagent components) that compute the 
UI DOM that should be displayed to the user.

To render the right UI, they need to source application state, which is
delivered reactively via the queries of Domino 4. They 
compute hiccup-formatted data, which is a description of the DOM required.

### Domino 6 - DOM

You don't write Domino 6 - it is handled for you 
by Reagent/React. I mention it here 
for completeness and to fully close the loop.

This is the step in which the hiccup-formatted 
"descriptions of required DOM", returned by the view functions of Domino 5, are made real.
The browser DOM nodes are mutated. 


### A Cascade Of Simple Functions

**Each of the dominoes you write are simple, pure functions** which 
can be described, understood and 
tested independently. They take data, transform it and return new data.

The loop itself is very mechanical in operation.
So, there's a regularity, simplicity and
certainty to how a re-frame app goes about its business,
which leads, in turn, to an ease in reasoning and debugging.

### Managing mutation

The two sub-cascades 1-2-3 and 4-5-6 have a similar structure.

In each, it is the second to last domino which 
computes "data descriptions" of mutations required, and it is 
the last domino which does the dirty work and realises these descriptions.

In both cases, you don't need to worry yourself about this dirty work. re-frame looks 
after those dominoes.

## Code Fragments For The Dominos

<img align="right" src="/images/Readme/todolist.png?raw=true">

So that was the view of re-frame from 60,000 feet. We'll now shift to 30,000 feet 
and look again at each domino, but this time with code fragments. 

**Imagine:** we're working on a SPA which displays a list of items. You have 
just clicked the "delete" button next to the 3rd item in the list.

In response, what happens within this imaginary re-frame app? Here's a sketch 
of the six domino cascade:

> Don't expect 
to completely grok the terse code presented below. We're still at 30,000 feet. Details later. 

### Code For Domino 1

The delete button for that 3rd item will have an `on-click` handler (function) which looks
like this:
```clj
 #(re-frame.core/dispatch [:delete-item 2486])
```

`dispatch` emits an `event`.

A re-frame `event` is a vector and, in this case, 
it has 2 elements: `[:delete-item 2486]`. The first element,
`:delete-item`, is the kind of event. The rest is optional, further data about the 
`event` - in this case, my made-up id, `2486`, for the item to delete.

### Code For Domino 2

An `event handler` (function), `h`, is called to 
compute the `effect` of the event `[:delete-item 2486]`.

Earlier, on program startup, `h` would have been 
registered for handling `:delete-item` `events` like this:
```clj
(re-frame.core/reg-event-fx   ;; a part of the re-frame API
  :delete-item                ;; the kind of event
  h)                          ;; the handler function for this kind of event
```

`h` is written to take two arguments: 
  1. a `coeffects` map which contains the current state of the world (including app state)
  2. the `event`
  
`h` returns a map of `effects` - a description 
of how the world should be changed by the event. 

Here's a sketch (we are at 30,000 feet):
```clj
(defn h 
 [{:keys [db]} event]                    ;; args:  db from coeffect, event
 (let [item-id (second event)]           ;; extract id from event vector
   {:db  (dissoc-in db [:items item-id])})) ;; effect is change db
```

re-frame has ways (beyond us here) to inject necessary aspects
of the world into that first `coeffects` argument (map). Different 
event handlers need to know different things about the world 
in order to get their job done. But current "application state"
is one aspect of the world which is invariably needed, and it is made 
available by default in the `:db` key.

### Code For Domino 3

An `effect handler` (function) actions the `effects` returned by `h`.

Here's what `h` returned:
```clj
{:db  (dissoc-in db [:items item-id])}
```
Each key of the map identifies one kind 
of `effect`, and the value for that key supplies further details. 
The map returned by `h` only has one key, so there's only one effect.

A key of `:db` means to update the app state with the key's value.

This update of "app state" is a mutative step, facilitated by re-frame
which has a built-in `effects handler` for the `:db` effect.

Why the name `:db`?  Well, re-frame sees "app state" as something of an in-memory 
database. More on that soon.

Just to be clear, if `h` had returned: 
```clj
{:wear  {:pants "velour flares"  :belt false}
 :tweet "Okay, yes, I am Satoshi. #coverblown"}
```
Then the two effects handlers registered for `:wear` and `:tweet` would 
be called in this domino to action those two effects. And, no, re-frame 
does not supply standard effect handlers for either, so you would have had 
to have written them yourself (see how in a later tutorial).

### Code For Domino 4

Because an effect handler just updated "app state",
a query (function) over this app state is called automatically (reactively), 
itself computing the list of items.

Because the items are stored in app state, there's not a lot 
to compute in this case. This 
query function acts more like an extractor or accessor:
```clj
(defn query-fn
  [db _]         ;; db is current app state
  (:items db))   ;; not much of a materialised view
```

On program startup, such a `query-fn` must be associated with a `query-id`, 
(for reasons obvious in the next domino) like this:
```clj
(re-frame.core/reg-sub  ;; part of the re-frame API
   :query-items         ;; query id  
   query-fn)            ;; query fn
```
Which says "if you see a query (subscribe) for `:query-items`, 
use `query-fn` to compute it".

### Code For Domino 5

Because the query function for `:query-items` just re-computed a new value, 
any view (function) which subscribes to `:query-items` 
is called automatically (reactively) to re-compute DOM.

View functions compute a data structure, in hiccup format, describing 
the DOM nodes required. In this case, there will be no DOM nodes 
for the now-deleted item, obviously, but otherwise the same DOM as last time.

```clj
(defn items-view
  []
  (let [items  (subscribe [:query-items])]  ;; source items from app state
    [:div (map item-render @items)]))   ;; assume item-render already written
```

Notice how `items` is "sourced" from "app state" via `subscribe`. 
It is called with a query id to identify what data it needs.

### Code For Domino 6

The DOM (hiccup) returned by the view function 
is made real by Reagent/React. No code from you required. Just happens.

The DOM computed "this
time" will be the same as last time, **except** for the absence of DOM for the
deleted item, so the mutation will be to remove some DOM nodes.

### 3-4-5-6 Summary

The key point to understand about our 3-4-5-6 example is:
  - a change to app state ...
  - triggers query functions to rerun ...
  - which triggers view functions to rerun
  - which causes new DOM 

Boom, boom, boom go the dominoes. It is a reactive data flow.

### Aaaaand we're done 

At this point, the re-frame app returns to a quiescent state, 
waiting for the next event.

## So, your job is ... 

When building a re-frame app, you:
 - design your app's information model (data and schema layer)
 - write and register event handler functions  (control and transition layer)  (domino 2)
 - (once in a blue moon) write and register effect and coeffect handler
   functions (domino 3) which do the mutative dirty work of which we dare not
   speak. 
 - write and register query functions which implement nodes in a signal graph (query layer) (domino 4)
 - write Reagent view functions  (view layer)  (domino 5)


## It is mature and proven in the large

re-frame was released in early 2015, and has since 
[been](https://www.fullcontact.com) successfully
[used](https://www.nubank.com.br) by
[quite](http://open.mediaexpress.reuters.com/) a 
[few](https://rokt.com/) companies and
individuals to build complex apps, many running beyond 40K lines of
ClojureScript.

<img align="right" src="/images/scale-changes-everything.jpg?raw=true">

**Scale changes everything.** Frameworks
are just pesky overhead at small scale - measure them instead by how they help
you tame the complexity of bigger apps, and in this regard re-frame has
worked out well. Some have been effusive in their praise.

Having said that, re-frame remains a work in progress and it falls
short in a couple of ways - for example it doesn't work as well as we'd
like with devcards, because it is a framework, rather than a library. 
We're still puzzling over some aspects and tweaking as we go. All designs
represent a point in the possible design space, with pros and cons.

And, yes, re-frame is fast, straight out of the box. And, yes, it has 
a good testing story (unit and behavioural). And, yes, it works in with figwheel to create
a powerful hot-loading development story. And, yes, it has 
fun specialist tooling, and a community,
and useful 3rd party libraries.

## Where Do I Go Next?

**At this point you 
already know 50% of re-frame.** There's detail to fill in, for sure,
but the core concepts, and even basic coding techniques, are now known to you.

Next you need to read the other three articles in the [Introduction section](/docs#introduction):

* [Application State](/docs/ApplicationState.md)
* [Code Walkthrough](/docs/CodeWalkthrough.md)
* [Mental Model Omnibus](/docs/MentalModelOmnibus.md)

This will push your knowledge to about 70%. The
final 30% will come incrementally with use, and by reading the other
tutorials (of which there's a few).

You can also experiment with these two examples: <br>
https://github.com/Day8/re-frame/tree/master/examples

Use a template to create your own project: <br>
Client only:  https://github.com/Day8/re-frame-template  <br>
Full Stack: http://www.luminusweb.net/

Use these resources: <br>
https://github.com/Day8/re-frame/blob/develop/docs/External-Resources.md

### T-Shirt Unlocked

Good news.  If you've read this far,
your insiders T-shirt will be arriving soon - it will feature turtles, 
[xkcd](http://xkcd.com/1416/) and something about "data all the way down". 
But we're still working on the hilarious caption bit. Open a
repo issue with a suggestion.

[SPAs]:http://en.wikipedia.org/wiki/Single-page_application
[SPA]:http://en.wikipedia.org/wiki/Single-page_application
[Reagent]:http://reagent-project.github.io/
