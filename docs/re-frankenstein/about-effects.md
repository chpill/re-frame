# re-frankenstein - about effects

Backward compatibility with re-frame is taken really seriously in this fork. The
idea was to be able to provide new features without changing too much the way
re-frame is used, particularly in terms of registering handlers, effects and
coeffects.

That is why in the first implementation of re-frankenstein, the `do-fx`
interceptor that was swapped in place of its original counterpart (on
`(frank/create)`) was strictly identical to it except for the use of a local
handler registry (instead of the global registry).

Sadly, the result of this is that, apart from the `:db` effect, no other effect
could cause affect the local db! So we are going to move just a little bit away
from the original `do-fx` implementation.


## What re-frankenstein changes in 0.0.3

The `do-fx` interceptor injected at the creation of a `frank` is now slightly
different from in the way it invokes the effect handlers:

```
;; The original way
(effect-fn value)

;; The re-frankenstein way
(effect-fn value {:dispatch!      #(dispatch!      frank %)
                  :dispatch-sync! #(dispatch-sync! frank %)})
```

The new `do-fx` interceptor provides the effect handler a dispatch function they
may call if they need to locally dispatch further. As the db and the handler
registry are local when using a `frank` passing a dispatch function to the
effects handler is in fact the only way for them to modify the local db of the
`frank` instance.

That means that when you registering an effect that you want to use locally on a
`frank`, you will need to pass a function of 2 arguments:

```
(reg-fx ::some-effect
        (fn [value {dispatch! :dispatch!}]
        ;; Do the side effecting here and dispatch if you need to modify state.
        ))
```

