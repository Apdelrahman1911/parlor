"""Read-only JVM classfile descriptor/code inspection. Never loads a JVM/class."""
import hashlib
import json
from pathlib import Path
import struct
import zipfile

OP_NAMES = ("nop aconst_null iconst_m1 iconst_0 iconst_1 iconst_2 iconst_3 iconst_4 iconst_5 lconst_0 lconst_1 fconst_0 fconst_1 fconst_2 dconst_0 dconst_1 bipush sipush ldc ldc_w ldc2_w iload lload fload dload aload iload_0 iload_1 iload_2 iload_3 lload_0 lload_1 lload_2 lload_3 fload_0 fload_1 fload_2 fload_3 dload_0 dload_1 dload_2 dload_3 aload_0 aload_1 aload_2 aload_3 iaload laload faload daload aaload baload caload saload istore lstore fstore dstore astore istore_0 istore_1 istore_2 istore_3 lstore_0 lstore_1 lstore_2 lstore_3 fstore_0 fstore_1 fstore_2 fstore_3 dstore_0 dstore_1 dstore_2 dstore_3 astore_0 astore_1 astore_2 astore_3 iastore lastore fastore dastore aastore bastore castore sastore pop pop2 dup dup_x1 dup_x2 dup2 dup2_x1 dup2_x2 swap iadd ladd fadd dadd isub lsub fsub dsub imul lmul fmul dmul idiv ldiv fdiv ddiv irem lrem frem drem ineg lneg fneg dneg ishl lshl ishr lshr iushr lushr iand land ior lor ixor lxor iinc i2l i2f i2d l2i l2f l2d f2i f2l f2d d2i d2l d2f i2b i2c i2s lcmp fcmpl fcmpg dcmpl dcmpg ifeq ifne iflt ifge ifgt ifle if_icmpeq if_icmpne if_icmplt if_icmpge if_icmpgt if_icmple if_acmpeq if_acmpne goto jsr ret tableswitch lookupswitch ireturn lreturn freturn dreturn areturn return getstatic putstatic getfield putfield invokevirtual invokespecial invokestatic invokeinterface invokedynamic new newarray anewarray arraylength athrow checkcast instanceof monitorenter monitorexit wide multianewarray ifnull ifnonnull goto_w jsr_w").split()
assert len(OP_NAMES) == 202


class Reader:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, length):
        if length < 0 or self.offset + length > len(self.data):
            raise ValueError("Truncated classfile")
        value = self.data[self.offset:self.offset + length]
        self.offset += length
        return value

    def number(self, count):
        return int.from_bytes(self.take(count), "big")


def parse(data):
    source = Reader(data)
    if source.number(4) != 0xCAFEBABE:
        raise ValueError("Not a classfile")
    minor, major = source.number(2), source.number(2)
    count = source.number(2)
    pool = [None] * count
    i = 1
    while i < count:
        tag = source.number(1)
        if tag == 1:
            pool[i] = (tag, source.take(source.number(2)).decode("utf-8", errors="backslashreplace"))
        elif tag in (3, 4):
            pool[i] = (tag, source.take(4).hex())
        elif tag in (5, 6):
            pool[i] = (tag, source.take(8).hex())
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = (tag, source.number(2))
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[i] = (tag, source.number(2), source.number(2))
        elif tag == 15:
            pool[i] = (tag, source.number(1), source.number(2))
        else:
            raise ValueError("Unknown constant pool tag " + str(tag))
        i += 1

    def constant(index, depth=0):
        if depth > 8:
            raise ValueError("Invalid constant recursion")
        value = pool[index]
        tag = value[0]
        if tag in (1, 3, 4, 5, 6):
            return value[1]
        if tag in (7, 8, 16, 19, 20):
            return constant(value[1], depth + 1)
        if tag == 12:
            return constant(value[1], depth + 1) + ":" + constant(value[2], depth + 1)
        if tag in (9, 10, 11):
            return constant(value[1], depth + 1) + "." + constant(value[2], depth + 1)
        if tag in (17, 18):
            return "bootstrap#" + str(value[1]) + " " + constant(value[2], depth + 1)
        return str(value)

    def attributes(reader):
        return [(constant(reader.number(2)), reader.take(reader.number(4)))
                for _ in range(reader.number(2))]

    access, own, parent = (source.number(2) for _ in range(3))
    interfaces = [constant(source.number(2)) for _ in range(source.number(2))]
    fields, methods = [], []
    for target in (fields, methods):
        for _ in range(source.number(2)):
            flags, name, descriptor = (source.number(2) for _ in range(3))
            row = {"name": constant(name), "descriptor": constant(descriptor), "flags": flags}
            for attribute, payload in attributes(source):
                if attribute == "Code":
                    reader = Reader(payload)
                    row["max_stack"], row["max_locals"] = reader.number(2), reader.number(2)
                    code = reader.take(reader.number(4))
                    row["code_sha256"] = hashlib.sha256(code).hexdigest()
                    row["instructions"] = disassemble(code, constant)
                    row["exception_table"] = [[reader.number(2) for _ in range(4)] for _ in range(reader.number(2))]
                    attributes(reader)
                    if reader.offset != len(payload):
                        raise ValueError("Unused Code bytes")
            target.append(row)
    attrs = attributes(source)
    if source.offset != len(data):
        raise ValueError("Unused class bytes")
    return {"version": [major, minor], "class": constant(own), "super": constant(parent),
            "interfaces": interfaces, "fields": fields, "methods": methods,
            "source_file": [constant(int.from_bytes(value, "big")) for name, value in attrs if name == "SourceFile"]}


def disassemble(code, constant):
    pos, result = 0, []
    while pos < len(code):
        start = pos
        opcode = code[pos]
        pos += 1
        if opcode >= len(OP_NAMES):
            raise ValueError("Unrecognized opcode")
        row = {"offset": start, "opcode": OP_NAMES[opcode]}
        if opcode in (170, 171):
            pos += (-pos) % 4
            default = struct.unpack(">i", code[pos:pos + 4])[0]
            pos += 4
            if opcode == 170:
                low, high = struct.unpack(">ii", code[pos:pos + 8]); pos += 8
                offsets = [struct.unpack(">i", code[pos + n * 4:pos + n * 4 + 4])[0] for n in range(high - low + 1)]
                pos += 4 * len(offsets)
                row["cases"] = [[low + n, start + offset] for n, offset in enumerate(offsets)]
            else:
                count = int.from_bytes(code[pos:pos + 4], "big"); pos += 4
                row["cases"] = [[struct.unpack(">i", code[pos + n * 8:pos + n * 8 + 4])[0], start + struct.unpack(">i", code[pos + n * 8 + 4:pos + n * 8 + 8])[0]] for n in range(count)]
                pos += count * 8
            row["default_target"] = start + default
        elif opcode == 196:
            following = code[pos]; length = 5 if following == 132 else 3
            row["wide_operands_hex"] = code[pos:pos + length].hex();pos += length
        else:
            length = (1 if opcode in {16,18,21,22,23,24,25,54,55,56,57,58,169,188} else
                      2 if opcode in {17,19,20,132,178,179,180,181,182,183,184,187,189,192,193,198,199} or 153 <= opcode <= 168 else
                      3 if opcode == 197 else 4 if opcode in {185,186,200,201} else 0)
            operands = code[pos:pos + length]
            if len(operands) != length:
                raise ValueError("Truncated instruction")
            if opcode in {18,19,20,178,179,180,181,182,183,184,185,186,187,189,192,193,197}:
                index = int.from_bytes(operands[:1 if opcode == 18 else 2], "big")
                row["constant_index"], row["constant"] = index, constant(index)
            elif 153 <= opcode <= 168 or opcode in {198,199,200,201}:
                row["target"] = start + int.from_bytes(operands, "big", signed=True)
            elif length:
                row["operands_hex"] = operands.hex()
            pos += length
        result.append(row)
    if pos != len(code):
        raise ValueError("Bad instruction length")
    offsets = {row["offset"] for row in result}
    for row in result:
        if "target" in row and row["target"] not in offsets:
            raise ValueError("Branch does not land on an instruction")
    return result


def main():
    here = Path(__file__).resolve().parent
    cli = Path("/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/lib/sdklib/sdklib.core.jar")
    record = {"method": "Read public ZIP/classfile bytes with Python only; no JVM/native/class initialization", "jar": str(cli), "jar_sha256": hashlib.sha256(cli.read_bytes()).hexdigest(), "classes": []}
    with zipfile.ZipFile(cli) as archive:
        for name in ("EmulatorPackages", "EmulatorPackage"):
            path = "com/android/sdklib/internal/avd/" + name + ".class"
            data = archive.read(path)
            result = parse(data)
            result.update(entry=path, sha256=hashlib.sha256(data).hexdigest())
            record["classes"].append(result)
    with (here / "installed-emulator-package-classfile-01.json").open("x") as output:
        json.dump(record, output, indent=2)
        output.write("\n")


if __name__ == "__main__":
    main()
