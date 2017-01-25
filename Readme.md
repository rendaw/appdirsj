`appdirsj` is a cross-platform library to get filesystem paths for standard locations (user config directory, system cache directory, etc).

This is a Java port of [ActiveState/appdirs](https://github.com/ActiveState/appdirs).  I hope it is a fairly literal translation, as far as path determination logic is concerned.

Main changes:

1. Only the JNA codepath is used to get Windows paths.  JNA is required.
2. Static methods have been removed to reduce parameter proliferation.  A fluent interface is available instead.
