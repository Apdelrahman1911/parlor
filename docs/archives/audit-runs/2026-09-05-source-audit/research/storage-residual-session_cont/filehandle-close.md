# closeFile()

Disallows further access to the represented file or communications channel and signals end of file on communications channels that permit writing.

func closeFile()## Discussion

If the file handle object owns its file descriptor, it automatically closes that descriptor when it is deallocated. If you initialized the file handle object using the init(fileDescriptor:) method, or you initialized it using the init(fileDescriptor:closeOnDealloc:) and passed false for the `flag` parameter, you can use this method to close the file descriptor; otherwise, you must close the file descriptor yourself.

After calling this method, you may still use the file handle object but must not attempt to read or write data or use the object to operate on the file descriptor. Attempts to read or write a closed file descriptor raise an exception.

