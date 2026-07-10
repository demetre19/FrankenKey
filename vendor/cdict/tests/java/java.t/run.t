  $ cdict-tool build -o dict main:ocaml_manual.txt
  Parsing "ocaml_manual.txt"
  Built dictionary "main" (7984 words)
  Done.

  $ java -cp cdict_java.jar -Djava.library.path=$PWD juloo.cdict.tests/CdictJavaTests.java -- dict
  spatial exact: types cost=0 edits=0 mask=0
  spatial substitution: types cost=4096 edits=1 mask=1
  spatial omission: types cost=2048 edits=1 mask=2
  spatial extra-tap: types cost=2048 edits=1 mask=4
  spatial transposition: types cost=256 edits=1 mask=8
  spatial bounded deterministic ordering: passed
  spatial immutable bounds and validation: passed
