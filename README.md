# What is it?

Glue is a simplistic dynamically typed scripting language based on a more complex fully typed language.
The syntax and execution logic is based mostly on java except for the "code blocks" which have a python twist in that **white space is used** instead of brackets.

# Hello World!

Glue comes with a command line client, you can look at the included example bash script "glue.sh" to see how you can run glue.

There are a few parameters that you can pass in but all of them have sensible defaults:

- extension: the extension of the glue scripts (default "glue")
- charset: the character set used by the glue engine to parse scripts and files (default "UTF-8")
- environment: you can set the environment glue should use to run the script (default "local")
- debug: if set to "true", it will enable some output logging (default "false")
- trace: if set to "true", it will allow you to trace through the code line by line. It will also activate debug. (default "false")
- path: a list of file paths where glue should look for scripts, the separator is dependent on your OS and should match the one you use to separate class paths. (default is the "PATH" environment variable) 

Once you have glue set up correctly with the glue.sh file on your path and the dependencies in place, create a file called "hello.glue" in any folder listed in the PATH variable. Add the following line to it:

```
echo("Hello World!")
```

In a console, run: `glue hello`

This will print out: `Hello World!`

## Available Methods

You can run `glue -l` to get a list of all available methods.

# Concepts

## Environments

Glue was developed with a focus on multiple environments. This means you should be able to run the same script but with different parameters depending on which environment you were running it on.
One of the ways glue allows you to do this is by creating a configuration file called ".glue" that resides in your home folder.

For example suppose you want to be able to run your scripts on three environments: "local" (the default), "dev" and "qlty", you can add this to the file:

```
local.endpoint = http://localhost:8080/endpoint
dev.endpoint = http://dev.example.com/endpoint
qlty.endpoint = http://qlty.example.com/endpoint
```

This means for all three environments there is now a variable called "endpoint" which has a different value depending on where you run it.
You can access these variables using the method "environment":

```
endpoint = environment("endpoint")
```

Another way that you can define environment-specific variables is to use labels.

# Syntax

## Labels

You can add a label to any line and it will determine in which environment a line is executed, the above configuration example can also be setup like this:

```
LOCAL: 	endpoint = "http://localhost:8080/endpoint"
DEV: 	endpoint = "http://dev.example.com/endpoint"
QLTY: 	endpoint = "http://qlty.example.com/endpoint"
```

The lines will only be executed if the label matches the environment it is being run upon.

## Optional Assignments

When you are creating your script, you can assign a value to a variable in two ways:

```
myVar = "test"
myVar ?= "test"
```

The difference between the two is that on the first line, the value is always assigned but on the second line it is only assigned if it doesn't exist yet.
These "optional assignments" are also used to detect the input parameters of a script. They are assigned in the order that they are detected so don't move them too much.
Additionally it is best practice to put these optional input parameters at the top of a script for easy readability.

## Comments

There are two types of comments:

```
# This is an entire comment line
myVar = "test"				# This is a line-specific comment
```

Both comments are almost identical with the difference that the second comment is actually added to the execution unit in the background which allows you to add contextual information to an execution line.

### Script Description

Note that **all full line comments** before the **first line of code** are regarded as a description of the script. This means they will be merged and shown as the general description of what the script does if needed. 

## Annotations

It is possible to set annotations on lines. Currently the annotations do not have much use except descriptive but other uses will likely be given to them in the future.

```
@tag = test, someOtherTag
myVar = "test"
```

### Script annotations

All annotations before the **first line of code** are interpreted as script level annotations. These can be things like "@deprecated" or "@tag" which can later be used to group scripts by descriptive tags.
Keep in mind that this also means you can't actually set annotations on the first line of code.

## Method Calls

Out of the box, glue will allow you to call either static java methods or other glue scripts. This can easily be extended to call webservices and the like.
Suppose you have this line:

```
myDate = format(now(), "yyyy-MM-dd")
```

The engine will by default first look for a script called "format". If it does not find it, it will look in a configurable list of java classes if there is a static method called format.
You can also call static java methods in a class you did not configure, but then you have to give the full class name:

```
myDate = com.example.DateUtils.format(now(), "yyyy-MM-dd")
```

Note that for script calls, the arguments are applied to the script **in the order that they are defined**. Suppose for example that you have a script called format that looks like this:

```
date ?= now()
format ?= "yyyy/MM/dd"
...
formattedDate = ...	
```

The first argument in the format() call will be applied to "date" while the second will be put in the variable "format". Important to note is however that the original variable "myDate" now holds the **entire variable set** of the format call, to actually get the formatted date we would need to do this:

```
result = format(now(), "yyyy-MM-dd")
myDate = result/formattedDate
```

In the "result" variable will be any variable created (and not specifically removed) by the script you called.

When calling a script you can also leave one one or more parameters at the end, for example in this case we could have called:

```
result = format()
```

In this case the format script would simply use the default values it has.

## Switch

There is a switch statement that is actually a mixture of a regular java switch and an if/elseif structure. This is perhaps best explained by an example (available in the testcases of glue):

```
LOCAL: 	name ?= "alex"		# default value on LOCAL
DEV: 	name ?= "john"		# default value on DEV

switch(name)
	case ("alex")
		isNameAlex = true
	case ("john")
		isNameJohn = true
		
switch
	case (name == "alex")
		isNameReallyAlex = true
	case (name == "john")
		isNameReallyJohn = true
		
isAlexForSure = isNameAlex != null && isNameReallyAlex != null && isNameAlex && isNameReallyAlex
isJohnForSure = isNameJohn != null && isNameReallyJohn != null && isNameJohn && isNameReallyJohn
```

As you can see, the first switch statement is the one you are likely already familiar with: you check each case to see if it matches the given value of the variable "name".
The second switch is more like the if/elseif construct because there is no value to switch on, it will simply evaluate all the case statements until a match is found.
There is also support for "default" of course.

## If

There is an if structure there is **no else if or else**. It can be used for a quick check but if you need the else if/else concept, please use a switch.

```
if (name == "alex")
	echo("it's alex!")
```

## For

You can loop over the elements in an array or a collection using a for loop. The syntax is:

```
for (myVar : range(0, 10))
	doSomething(myVar)
```

Each element available in the return value of range will be put in the variable "myVar" which will only exist in the scope of the for loop. You can also leave out the variable name:

```
for (range(0, 10))
	doSomething($value)
```

In this case the variable is inserted as "$value".
Note that the index is **always** injected as "$index" though nested for loops *will* overwrite each others' indexes. This means you can also do this:

```
myRange = range(0, 10) 
for (myRange)
	doSomething(myRange[$index])
```

## Multiline Expressions

You can spread a command over multiple lines by adding additional depth to the next line(s). Note that this does **not work** for statements that start a new code block. The lines will be merged with one space between them so for example:

```
result = isState1
	&& isState2
	&& isState3
						&& isState4 
```

Will be merged into the following before it is actually parsed:

```
result = isState1 && isState2 && isState3 && isState4 
```

This however is **not valid** as the "for" command starts a new code block:

```
for (field :
	range(0, 10))
```


## Variable handling

There is support for an xpath like syntax on variables, suppose you have the following classes:

```java
public class Company {
    private String name, unit, address, billingNumber;
    private List<Employee> employees;
}

public static class Employee {
    private String id, firstName, lastName;
    private Integer age;
    private Date startDay;
}
```

You can do:

```
myCompany = ...
for (employee : myCompany/employees[age > 60])
	congratulate(employee)
``` 

# Tracing

The code comes with the necessary tools to enable you to trace through your code step by step. The CLI implementation has an example of how this can be done.
When tracing there is a timeout that can be configured as an environment variable "timeout" (default is Long.MAX_VALUE). After this timeout, the script will simply continue and you will be out of tracing mode.

# Advanced Use

While glue comes with a default CLI client, it was primarily developed to plug it into other systems. As such you have control over a huge number of aspects should you decide to delve deeper. For example (this list is not exhaustive):

- You can redirect the output of any runtime to somewhere else
- You can trace to any breakpoint (not just the next line)
- You can plug in custom ways to resolve methods, e.g. webservices
- You can go further and plug in custom ways of calculating stuff, for example if you do a lot of date handling you can allow: `date = now() + "1month"`
- You can set up custom repositories (e.g. stream scripts from HTTP or fetch them from an FTP)
- You can plug in custom parsers to develop a new syntax or add to the existing one
...

Of course because of this extensibility, all the above is only valid for the default setup.