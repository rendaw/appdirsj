`appdirsj` is a cross-platform library to get filesystem paths for standard locations (user config directory, system cache directory, etc).

Get with maven:
```
<dependency>
    <groupId>com.zarbosoft</groupId>
    <artifactId>appdirsj</artifactId>
    <version>0.0.2</version>
</dependency>
```
Other ways to include it in your project: [Maven Central](https://search.maven.org/#artifactdetails%7Ccom.zarbosoft%7Cappdirsj%7C0.0.2%7Cjar).

This is a Java port of [ActiveState/appdirs](https://github.com/ActiveState/appdirs).  I hope it is a fairly literal translation, as far as path determination logic is concerned.

Main changes:

1. Only the JNA codepath is used to get Windows paths.  JNA is required.
2. Static methods have been removed to reduce parameter proliferation.  A fluent interface is available instead.
