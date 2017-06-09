## 0.0.3-SNAPSHOT

#### Breaking

- Effect handlers get a map as second argument containing the dispatch and
  dispatch-sync functions


## 0.0.2 (19/05/2017)

#### Breaking

- Effects handlers to be used on a `frank` instance should now be a function of
  2 arguments:
  * the effect value
  * the dispatch function to use in the handler

  See the [doc](Docs/re-frankenstein/about-effects) for more details.

## 0.0.1 (17/05/2017)

Original release
