# object(forInfoDictionaryKey:)

Returns the value associated with the specified key in the receiver’s information property list.

func object(forInfoDictionaryKey key: String) -> Any?## Return Value

The value associated with `key` in the receiver’s property list (`Info.plist`). The localized value of a key is returned when one is available.

## Discussion

Use of this method is preferred over other access methods because it returns the localized value of a key when one is available.

