# lein-gossip

Gossip is a lein tool to generate call-graphs for Clojure code.

This is the first pass and works in a fairly naive way just by treating Clojure code
as data and looking for symbols. It currently doesn't work for Java imports and is
a bit rough around the edges. It was originally written when I inherited a medium
sized codebase written in Clojure and I had no idea what was going on where.

Future Features

1. Show callers from other packages.
2. Handle Java imports.
3. Use an AST library for improved discovery.

## Important

This tool reads your source code files in src and writes DOT files in docs/dot/. It has been
tested a bit but not widely tested.

USE AT YOUR OWN RISK

It hasn't erased any of my projects yet but YMMV. Make a copy.

## Installation

For now, you can download this project, unpack, cd into the directory and type:

    lein install

Then for any project that you want to use Gossip with, you can add a .lein-classpath file that
points to the src directory of the lein-gossip project.

To use Gossip, type:

    lein gossip

## Future Installation

Use this for user-level plugins:

Put `[lein-gossip "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-gossip 0.1.0-SNAPSHOT`.

Use this for project-level plugins:

Put `[lein-gossip "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your project.clj.


## Usage

In your project, type:

    $ lein gossip

The DOT files will be placed in project\_name/docs/dots and you can process them with GraphiViz
or OmniGraffle.

## License

Copyright © 2013 Stephyn G. W. Butcher

Distributed under the Eclipse Public License, the same as Clojure.
