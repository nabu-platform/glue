Sandbox mode:

- disabled system integration (no cli commands)
- disabled access to non-whitelisted java classes (no accessing random static java methods)
- disabled parallellization methods (no generating undue load)
- disabled file access methods (no manipulating of file system)
- disabled nabu service integration (no using nabu services to do any of the above)
	- this includes service integration for the eai-module-glue package (internal)
	- as well as service integration with glue-integrator (external)
- disabled input() method to request user interaction
- disabled bash() and exec() methods
- limit for loop to 1000 iterations (no looping over infinite lists)
- limit series.resolve to 1000 entries and don't let resolve run in parallel (no resolving of infinite lists and no parallellization)
- limit the script execution time (default is 15s) (no generating long-term load)
