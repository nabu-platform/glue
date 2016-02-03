# What is it?

Glue is a simplistic optionally typed functional scripting language based on a more complex fully typed service oriented language.
The syntax and execution logic is based mostly on java except for the "code blocks" which have a python twist in that **white space is used** instead of brackets.

## Design goals:

- **Lightweight syntax**: syntax is only introduced to increase readibility, not to decrease character count
- **Easy to use**: focus on readability and ease of use by limiting the amount of concepts you need to grasp to make something work (e.g. namespaces are supported but not mandatory)
- **Modular**: everything is a method and method resolving is fully dynamic, even the core methods can be removed if required. Apart from that pretty much everything is pluggable, the typing system, the syntax parser,...
- **Hooks**: glue has a very extensive hook system allowing you to do pretty much anything at runtime. The loggers take advantage of this to format the output in different ways. The web framework also uses this hook system to optionally execute steps based on permission annotations.
- **Immutability**: in the default glue packages all data is immutable 

## Scalability

Glue can scale to many requirements, for example:

- [Glue Testing](http://www.glueverse.be/): an automated testing framework that incorporates support for selenium, soapui, RMI via soap,...
- [Glue Web Framework](https://github.com/nablex/http-glue): a web-framework that adds a whole bunch of methods and interesting annotations to rapidly build web applications
- **Integration Scripting Language**: glue is based on a fully statically typed integration environment and there is also a compatibility layer that integrates glue fully with that backend environment (both at the typing and method level)
- **System Management**: glue integrates fully with the commandline, allowing very complex system management scripts
- **Partial FoxPro Reimplementation**: In order to run pre-existing fox-pro scripts, the subset of foxpro methods used was reimplemented in glue to allow it to run them
...

## Advanced Features

A number of optional advanced features have been introduced that are not unique to glue:

- **Named Parameters**: both anonymous and named parameters are allowed
- **Operator Overloading**: for example we added easy date management using: `date = now() + "1month"`
- **Virtual File System**: the file methods (read, write,...) actually work on a virtual file system allowing you to plug in transparent support for other protocols
- **Lambdas**: lambdas that fully enclose their originating scope are functional
- **Lazy Lists**: the haskell-esque lazy lists [are available](https://github.com/nablex/glue-series)

There are some advanced features that are somewhat unique to glue:

### Quirks

A lot of what you'll see in glue is pretty standard as far as programming languages go, but there are some quirks that are not generally available in most languages:

- **Multiple Return**: all script executions have multiple return: they give you (read) access to all the variables used by the script that was called
- **Advanced Querying**: glue runs on an evaluation engine that by default has a xpath-like syntax, allowing not only `employees[1]` but also `employees[age > 30]`
- **Resources**: every script has an (optional) resources folder attached to it, allowing you to add additional files (templates, results,...) to any script

Also: breakpoints in CLI!

## Runnability

Glue is based fully on java and has been extensively tested on linux, windows & mac.

To run glue scripts there are a few default options:

- a commandline client
- an interactive shell
- a batch runner (can be run from e.g. Jenkins)
- a custom IDE

# Hello World!

In the command line client, there are a few parameters that you can pass in but all of them have sensible defaults:

- extension: the extension of the glue scripts (default "glue")
- charset: the character set used by the glue engine to parse scripts and files (default "UTF-8")
- environment: you can set the environment glue should use to run the script (default "local")
- debug: if set to "true", it will enable some output logging (default "false")
- trace: if set to "true", it will allow you to trace through the code line by line. It will also activate debug. (default "false")
- path: a list of file paths where glue should look for scripts, the separator is dependent on your OS and should match the one you use to separate class paths. (default is the "PATH" environment variable)
- label: you can set a custom environment field to evaluate instead of the environment name to see if labels should be executed 

Once you have glue set up correctly with the glue.sh file on your path and the dependencies in place, create a file called "hello.glue" in any folder listed in the PATH variable. Add the following line to it:

```
echo("Hello World!")
```

In a console, run: `glue hello`

This will print out: `Hello World!`

## Available Methods

You can run `glue -l` to get a rudimentary list of all available methods. You can also run `glue document` to generate javadoc-like documentation of all the available methods.

A better way to find scripts or read the information about them is to run `glue man <name>` which will find all scripts that match the given name. The wildcard `*` is allowed and it is a case insensitive search. For example if you want to find all scripts that have the word "test" in their name, use `glue man *test*`.

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

Note that label matching is a configurable feature, the default is environment matching but you can match any environment variable, suppose you have the following in your configuration:

```
local.version = 2
dev.version = 1
```

You can run glue with the additional parameter: `glue label=version ...` which means in the script you can use:

```
1: doThisInOldCode()
2: doThisInNewCode()
```

## Optional Assignments

When you are creating your script, you can assign a value to a variable in two ways:

```
myVar = "test"
myVar ?= "test"
```

The difference between the two is that on the first line, the value is always assigned but on the second line it is only assigned if it doesn't exist yet.
These "optional assignments" are also used to detect the input parameters of a script. They are assigned in the order that they are detected or by name.
Additionally it is best practice (though not mandatory) to put these optional input parameters at the top of a script for easy readability.

## Comments

You can put a comment before a line of code or after it:

```
# This is an entire comment line
myVar = "test"				# This is a line-specific comment
```

Both comments are identical in nature and will actually be concatenated for the comment field
A comment is aimed at whoever is **reading the code**

## Descriptions

Descriptions are very much like comments but with a **different target audience**: people who **read the result of a run**

```
## This is an entire description line
myVar = "test"				## This is a line-specific description
```

The only difference with a comment is the use of a double hashtag instead of a single.

### Script Description

Note that **all full line comments** before the **first line of code** (or an empty line) are regarded as a description of the script. This means they will be merged and shown as the general description of what the script does if needed. 

## Annotations

It is possible to set annotations on lines. The annotation can be used to accomplish something (permission checks, additional documentation,...) or can be merely descriptive.

```
@tag = test, someOtherTag
myVar = "test"
```

### Used annotations

Some annotations are used by the core system.

#### Disabled

```
@disabled
doSomething()
```

This disables the line so it is not executed if you run the script.

#### Breakpoint

You can set a breakpoint on any line and the execution will halt before that line **if** you are in trace mode.
In trace mode there is also the option of (temporarily) turning this breakpoint off or ignoring all breakpoints.

```
@breakpoint
doSomething()
```

#### Testcase

The `@testcase` annotation can be added to the script level to indicate that a script should be picked up by gluet (the test runner). The selection based on this annotation is also pluggable.
Gluet can be run by jenkins and will simply execute all the scripts on its path with this annotation.

### Script annotations

All annotations before the **first line of code** (or an empty line) are interpreted as script level annotations. These can be things like "@deprecated" or "@tag" which can later be used to group scripts by descriptive tags.
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

### Named Parameters

Glue optionally supports named parameters which can be turned on (default) or off by setting the system property `named.parameters`.

For example suppose you have a script `test`:

```python
a ?= null
b ?= null
c ?= null
echo(a, b, c)
```

And you have a script `test2`:

```python
test(1, 2, 3)
```

This will print out:

```
1
2
3
```

However you could also do this:

```python
test(c: 1, b: 2)
```

This would print out:

```
null
2
1
```

A slightly more complex example:

```python
test(c: test(b : "something")/b)
```

Which would print:

```
null
something
null
null
null
something
```

Right before the first execution of an operation, glue will perform a rewrite of said operation (if enabled) that will remap the parameters based on their naming.

Additional care has to be taken for java methods as they support overloading based on amount of parameters. The rewrite will always use the definition with the most parameters to perform the mapping but will only send along the max amount of parameters declared by the user (as long as this is at least the minimal amount of parameters expected).

So for instance if you have two java methods:

```java
public Integer sum(Integer a, Integer b, Integer c);
public Integer sum(Integer a, Integer b);
```

And in glue the user writes:

```python
sum(arg0: 1)
```

Glue will use the first description (the longest) to map the "arg0" parameter. Based on the user declaration, the highest declared parameter index is "0" (the first parameter). However the smallest sum() method known requires at least two parameters so glue will fill in the second parameter as "null" as part of the rewrite process.

Special care has to be taken with varargs in combination with overloading. Only the longest match should use varargs to avoid incorrect rewriting.

As an additional note the parameter here is exposed as "arg0" because no GlueParam annotation has been set.

#### Varargs

Glue provides a method called join() which looks like this in java:

```java
public static String join(@GlueParam(name = "separator") String separator, @GlueParam(name = "strings") String...strings)
```

In glue you can call this using named parameters:

```python
>> echo(join(strings: "this", "is", "a test!", separator: " "))
this is a test!
```

Which is equivalent of building the array yourself:

```python
>> echo(join(strings: array("this", "is", "a test!"), separator: " "))
this is a test!
```

Note that varargs are also supported at the script level (unless you set `script.varargs` to false) if you specifically define the **last** input variable as an array. For example if we have `test.glue`:

```python
[] a ?= null
echo(size(a))
```

And `test2.glue`

```python
test("a", "b", "c")
```

Then `glue test2` will print `3`

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

You can also pass a number to the for loop and it will iterate that many times.

```
for (10)
	echo("This is the " + $index + "th loop")
```

This will run 10 times with the index range [0, 9]

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
for (field : record)
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

## Arrays, tuples & maps

### Arrays

A lot of the glue methods are built on arrays, personally I like lists more but arrays have the distinct advantage that they integrate well with varargs.

An array is simple a number of elements clumped together, when adding to the array, the code will attempt to always correctly type the array unless it can't.
Glue will always merge arrays whenever you combine them, for example:

```
example = array("string1")
example = array(example, "strings2")
```

In the end, `example` will be an array of strings (`String[]`) with two elements in it.
Glue will also ignore null values when merging, so you can do this:

```
anotherExample = array(anotherExample, "strings2")
```

The variable `anotherExample` did not exist before this line of code so it will be null which means, after this line anotherExample is an array with **1** element. This allows you to easily build arrays in loops.

To access elements in arrays and tuples, you can use the default array syntax:

```
example = array("string1", "string2")
echo(example[0])							# Will print out "string1"
```

There is also a pseudo access mode that is especially interesting when combining tuples with arrays:

```
example = array(
					tuple("a", "b", "c"),
					tuple("d", "e", "f")
	)

echo(example[0])					# Regular access to print out [a, b, c]
echo(example/$0) 					# Pseudo access to print out [a, b, c], the array syntax is preferred over this
result = example[$0 == "d"]/$1		# The true reason for pseudo access: to search for things. In this case, "result" will be an array that contains one item: "e".
```

Why is result an array? The engine executing the query in the background sees that you are selecting zero or more elements that match your specific requirement `$0 == "d"`. Because multiple matches are possible, it will always return an array of them instead of just one.

### Tuples

Tuples are **immutable** lists of values. In the background, an unmodifiable List object is used. They are not automerged like arrays nor can they be expanded.
Tuples are mostly useful when combined with arrays or maps because you can never build an array of arrays (due to automerging) but you _can_ build an array of tuples.
In essence tuples are nameless objects with nameless fields that can only be accessed using array or pseudo access.
Combine them with maps however and the story changes.

### Maps

Maps basically allow you to take the concept of unnamed tuples and give them a name. Take for example this example:

```
map = map(			"field1", 			"field2", 			"field3",
		tuple(		"a",				"b",				"c"		),
		tuple(		"b",				"c",				"d"		),
		tuple(		"c",				"d",				"e"		),
		tuple(		"d",				"e",				"f"		)
	)
```

In the background this will build an array of maps (`Map[]`), one for each tuple, where the keys match the keys you defined at the top. In theory you can add a field at any time, doing something like:

```
map = map(			"field1", 			"field2", 			"field3",
		tuple(		"a",				"b",				"c"		),
		tuple(		"b",				"c",				"d"		),
																			"field4",
		tuple(		"c",				"d",				"e",			"f"		),
		tuple(		"d",				"e",				"f",			"g"		)
	)
```
  
Though it might be harder to manage such a map.
The true power of this map structure is however the way you can access the data, you can for example do:

```
for (record : map)
	echo(record/field1 + " - " + record/field4)
```

This will print out:

```
a - null
b - null
c - f
d - g
```

It basically allows for a primitive type of object creation. This is especially useful for building a map that contains inputs & expected outputs.
Part of the fun is that the syntax is also compatible with the way you would access actual objects, suppose you have these java objects:

```
class TestCase {
	String field1, field2, field3, field4;
}
```

You can do this in glue:

```
testcases = getTestCases()			# suppose this is a List or array of TestCase instances
for (record : testcases)
	echo(record/field1 + " - " + record/field4)
```

If you fill in the same value for the different fields, this exact same code will also work for the java-managed testcases.

# Variable Replacement

## Inline Variables

Each parser must implement a `substitute()` method. This method should replace variables in a string. The glue parser supports two ways of replacing:

```
This is my conclusion: ${conclusion}
```

In this text, the string `${conclusion}` will be replaced with the value of the variable `conclusion`. Important is that you can do any computation here that you can also do in an assignment in glue where the returned value is inserted, so for example:

```
This is my result: ${padLeft("0", 11, myResult + 1)}
```

This will pad your result with zeroes all the way up to eleven characters, for example if your variable is the number `9000`, it will print `00000009001`.

## Inline Script

Apart from inline variables, you can also inline an entire script. A script however has no return value, so instead it will print out everything that was echoed.

```python
Will he do it?
${{
switch(willHeDoIt)
	case (true)
		echo("He's done it!")
	default
		echo("Nope")
}}
```

Suppose the variable `willHeDoIt` is set to true, the output would be:

```
Will he do it?
He's done it!
```

**Important**: The output formatter used for inline scripts does **not** add linefeeds after each `echo()`, you have to add them yourself if you want them. 

# Structures

Because of the way glue works with multiple return, you can use scripts as "dynamic classes" which we generally refer to as `structures`, for example if you create a script `myStruct.glue`:

```python
a ?= null
b ?= null
```

You can do this:

```python
result = myStruct("1", "2")
echo(result/a, result/b)
```

Which will print:

```
$ glue test
1
2
```

The main reason to use structures is to combine multiple values that belong together, for example suppose you want to have a "person" who has a first name, a last name and an address.
Now suppose you want to remove the person from a database (if he exists) and insert him again, you could do:

```python
deletePerson("John", "Doe", "Somewhere")
createPerson("John", "Doe", "Somewhere")
```

The downside to this is that you spread out the inherently linked variables so what you could do is create a script called `structurePerson.glue`:

```python
firstName ?= null
lastName ?= null
address ?= null
```

And use it:

```python
person = structurePerson("John", "Doe", "Somewhere")
deletePerson(person)
createPerson(person)
```

## Dynamic structure generation

Using the named parameter syntax you can also create structures on the fly:

```python
person = structure(firstName: "John", lastName: "Doe", address: "Somewhere")
```

This can also be used to create a clone of an existing structure with an update of a field or a merge of multiple:

```python
fullName = structure(firstName: "John", lastName: "Doe")
person = structure(fullName, address: "Somewhere")
example = structure(age: 16, year: "fourth")
student = structure(person, example, firstName: "Alex")
```

Note that in this example `person/firstName` is "John" while `student/firstName` is "Alex". Structures remain immutable.

# Optional Typing

Glue is dynamically typed but that means when performing operations the code has to decide which type will "win". The rule used is that the left operand wins, so:

```python
echo("5" + 5)
echo(5 + "5")
```

This outputs:

```
55
10
```

On the first line we have a string on the left so the right operand is cast to a string and concatenated.
On the second line we have an integer on the left so the string is cast to a number and summed.

The downside of such dynamic behavior is that within a script, you generally know what the types are or can rapidly deduce it from the context, but if the value comes from outside the script as an input variable, you are fully dependent on that external call.

For example, what does this do?

```python
a ?= null
b ?= null
echo(a + b)
```

That is entirely dependent on what is passed in, for example:

```python
test(1, 1)
test("1", 1)
test(1, "1")
test("1", "1")
```

This will print out:

```
2
11
2
11
```

We can add type declarations to the variables in the original script:

```python
integer a ?= null
integer b ?= null
echo(a + b)
```

We get a more consistent output:

```
2
2
2
2
```

This might not be important for your code or it might be, what does this do?

```python
a ?= null
while (a < 0)
	a = a + 1
	# do something
```

To handle situations like this where you need additional guarantees about the type, you can use optional typing:

```python
integer a ?= null
while (a + 1 > 0)
	# do something
``` 

This will cast whatever is assigned to `a` to an integer number.

## Structure Typing

Because the glue typing system is pluggable you can plug in your own type resolvers.
One resolver that can be turned on (default) or off by setting `structure.allow.types` is type resolving based on glue scripts themselves.

Let's take the example of the structures above, the problem is when creating the `createPerson.glue` script, you do:

```python
person ?= null
sql("insert into persons (firstName, lastName, address) values (:person/firstName, :person/lastName, :person/address)")
```

Which is nice except obviously (as with all dynamic typing) you have no control over what "person" actually is. I might call this script with a entirely different structure or a date or something.

What you can do with structure typing turned on is:

```python
structurePerson person ?= null
```

The glue code will check that the incoming variable is "compatible" with the defined script which means it must (at least) have all variables with the same name, in this case "firstName", "lastName" and "address".
This object does not have to be a glue script result, it can be a java object for example (this is also pluggable).

Note that the typing will also perform any casting that is necessary, for example if whatever is given to the script has a string in it but the structure defines that as an integer, you will get an integer at runtime.

### Default values

There is another feature that is turned on by default but can be turned off by setting `structure.allow.defaultValues` to false. It allows you to reuse default values.
This means if you have a structure with a field `myField` that has a default value, for example:

```python
myField ?= "myDefaultValue"
```

And you have another object that is identical to the structure except for the `myField` field, it is still allowed to pass and will get the value `myDefaultValue`.

# Tracing

The code comes with the necessary tools to enable you to trace through your code step by step. The CLI implementation has an example of how this can be done.
When tracing there is a timeout that can be configured as an environment variable "timeout" (default is Long.MAX_VALUE). After this timeout, the script will simply continue and you will be out of tracing mode.

# Lambdas

Glue supports lambdas which can be turned on (default) or off by toggling `script.lambdas`. For example you could create `test.glue`:

```python
a ?= null
echo(a(1, 2))
```

And `test2.glue`:

```python
test(lambda(x, y, x + y)) 
``` 

Note that the lambda function first takes all the names of the parameters that you will use and as last parameter the actual action to run.
Also note that lambdas take **precedence** when resolving a method, so you can effectively "hide" a public method by creating a variable that holds a lambda with the same name.

The lambdas fully enclose the environment where they are defined in, as such you could create `test.glue`:

```python
a ?= null
something = 5
echo(a(something, 2)) 
``` 

And define `test2.glue`:

```python
fixed = -5
dynamic = lambda(x, x * 2)
test(lambda(x, y, x+y+fixed+dynamic(fixed))) 
```

Note that the lambda has access to the variables defined in the originating context and as such also any lambdas that were defined there.