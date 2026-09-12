# contentsOfDirectory(atPath:)

Performs a shallow search of the specified directory and returns the paths of any contained items.

```
func contentsOfDirectory(atPath path: String) throws -> [String]
```

### path

The path to the directory whose contents you want to enumerate.

## Return Value

An array of NSString objects, each of which identifies a file, directory, or symbolic link contained in `path`. Returns an empty array if the directory exists but has no contents. In Objective-C, if an error occurs, this method returns `nil` and assigns an appropriate error object to the `error` parameter.

## Discussion

This method performs a shallow search of the directory and therefore does not traverse symbolic links or return the contents of any subdirectories. This method also does not return URLs for the current directory (”`.`”), parent directory (”`..`”), or resource forks (files that begin with “`._`”) but it does return other hidden files (files that begin with a period character). If you need to perform a deep enumeration, use the enumeratorAtURL:includingPropertiesForKeys:options:errorHandler: method instead.

The order of the files in the returned array is undefined.

**note:**  In Swift, this method returns a nonoptional result and is marked with the `throws` keyword to indicate that it throws an error in cases of failure.

You call this method in a `try` expression and handle any errors in the `catch` clauses of a `do` statement, as described in Error Handling in The Swift Programming Language and `About Imported Cocoa Error Parameters`.


