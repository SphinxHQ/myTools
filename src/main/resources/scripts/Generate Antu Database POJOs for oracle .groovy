package scripts

import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil

import java.util.regex.Matcher
import java.util.regex.Pattern

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */

packageName = "antu.gis.model"
typeMapping = [
        (~/(?i)int/)                      : "long",
        (~/(?i)number/)                   : "long",
        (~/(?i)float|double|decimal|real/): "double",
        (~/(?i)datetime|timestamp/)       : "Date",
        (~/(?i)timestamp/)                : "Timestamp",
        (~/(?i)date/)                     : "Date",
        (~/(?i)time/)                     : "Time",
        (~/(?i)clob/)                     : "String",
        (~/(?i)blob/)                     : "byte[]",
        (~/(?i)/)                         : "String"
]


FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable && it.getKind() == ObjectKind.TABLE }.each { generate(it, dir) }
}

def generate(table, dir) {
    def className = javaName(table.getName(),true)
    def fields = calcFields(table)
    new File(dir, className + ".java").withPrintWriter { out -> generate(out, className, fields, table) }
}

def generate(out, className, fields, table) {
    def key = DasUtil.getPrimaryKey(table).toString()
    String regex = ".*?\\((.*?)\\)";
    Pattern p = Pattern.compile(regex);
    Matcher m = p.matcher(key);
    String tabComment = table.getComment() == null ? URLDecoder.decode(table.getName(), 'UTF-8'): URLDecoder.decode(table.getComment(), 'UTF-8')
    if (m.find()) {
        key = m.group(1)
    }
    //DasUtil.getPrimaryKey(table).getKind()
    out.println "package $packageName ;"
    out.println "import antu.com.annotations.Column;"
    out.println "import antu.com.annotations.PrimaryKey;"
    out.println "import antu.com.annotations.TableName;"
    out.println "import java.util.Date;"
    out.println "import io.swagger.annotations.ApiModel;"
    out.println "import io.swagger.annotations.ApiModelProperty;"
    out.println "import org.springframework.context.annotation.Description;"

    out.println "/**"
    out.println "*$tabComment"
    out.println "*/"
    out.println "@Description(\"$className\")"
    out.println "@TableName(\"${table.getName()}\")"
    out.println "@PrimaryKey(value = \"$key\" ,autoIncrement = false)"
    out.println "@ApiModel(value = \"${table.getName()}\",description = \"$tabComment\")"
    out.println "public class $className {"
    out.println ""
    fields.each() {
        if (it.annos != "") out.println "  ${it.annos}"
        out.println ""
        out.println "   /**"
        out.println "   *${it.comment}"
        out.println "   */"
        out.println "   @Column(\"${it.coloumn.toUpperCase()}\")"
        out.println "   @ApiModelProperty(name = \"${it.comment}\",value = \"${it.name}\")"
        out.println "   private ${it.type} ${it.name} ;"
    }
    fields.each() {
        out.println ""
        out.println "  public ${it.type} get${it.name.capitalize()}() {"
        out.println "    return ${it.name};"
        out.println "  }"
        out.println ""
        out.println "  public void set${it.name.capitalize()}(${it.type} ${it.name}) {"
        out.println "    this.${it.name} = ${it.name};"
        out.println "  }"
        out.println ""
    }
    out.println ""
    out.println "}"
}

def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value
        fields += [[
                           name   : javaName(col.getName(), false),
                           comment: col.getComment() == null ? col.getName(): URLDecoder.decode(col.getComment(), 'UTF-8'),
                           coloumn: col.getName(),
                           type   : typeStr,
                           annos  : ""]]
    }
}

def javaName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    capitalize || s.length() == 1 ? s : Case.LOWER.apply(s[0]) + s[1..-1]
}

