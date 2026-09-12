# readData(ofLength:)

Reads data synchronously up to the specified number of bytes.

func readData(ofLength length: Int) -> DataThe number of bytes to read from the file handle.

## Return Value

The data available through the receiver up to a maximum of `length` bytes, or the maximum size that can be represented by an NSData object, whichever is the smaller.

## Discussion

If the handle represents a file, this method returns the data obtained by reading `length` bytes starting at the current file pointer. If `length` bytes aren’t available, this method returns the data from the current file pointer to the end of the file. If the handle represents a communications channel, the method reads up to `length` bytes from the channel. Returns an empty `NSData` object if the handle is at the file’s end or if the communications channel returns an end-of-file indicator.

 This method raises fileHandleOperationException if attempts to determine the file-handle type fail or if attempts to read from the file or channel fail.

