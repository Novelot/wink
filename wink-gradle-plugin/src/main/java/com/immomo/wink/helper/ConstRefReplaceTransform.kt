package com.immomo.wink.helper

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.immomo.wink.Constant
import com.immomo.wink.ResolvedClass
import com.immomo.wink.Settings
import com.immomo.wink.const_ref.ClazzConstMap
import com.immomo.wink.util.LocalCacheUtil
import com.immomo.wink.util.WinkLog
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.objectweb.asm.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream

/**
 * 常量引用替换优化问题
 * @author 刘云龙
 *
 * 两个问题未解决:
 * 1.类中新加常量字段;
 * 2.使用+号拼接字符串时,会被编译为拼接后的字符串,原本int,long等的类型也会发生变化,导致找不到常量;
 */
class ConstRefReplaceTransform(val project: Project) : Transform() {

    override fun getName(): String = "ConstRefReplaceTransform"

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> = TransformManager.CONTENT_CLASS

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> = TransformManager.PROJECT_ONLY

    override fun isIncremental(): Boolean = false

    override fun transform(transformInvocation: TransformInvocation?) {
        beforeTransform()
        onTransform(transformInvocation)
        afterTransform(transformInvocation)
    }

    private fun beforeTransform() {
        printCopyRight()
        deleteConstDir(project.rootDir.absolutePath)
    }

    private fun onTransform(transformInvocation: TransformInvocation?) {
        transformInvocation?.inputs?.forEach { input ->
            WinkLog.d("[常量引用替换] ---------------")
            WinkLog.d("[常量引用替换] intput=${input}")

            input.directoryInputs.forEach { clazzDir ->
                WinkLog.d("[常量引用替换] dirInput=${clazzDir}")

                //是否在白名单
                val isInWhiteList = Settings.env.options?.moduleWhitelist?.any {
                    clazzDir.file.absolutePath.contains(it)
                } ?: return@forEach
                if (!isInWhiteList) return@forEach

                clazzDir.file.walkBottomUp().forEach {
                    if (it.isFile && it.absolutePath.endsWith(".class")) {
                        val index = it.absolutePath.indexOfLast { it == '/' }
                        val substring = it.absolutePath.substring(index + 1)
                        if (!substring.startsWith("R.class")
                            && !substring.startsWith("R$")
                            && !substring.startsWith("BuildConfig")
                            && !substring.startsWith("ARouter")
                        ) {
                            val classReader = ClassReader(FileInputStream(it.absolutePath))
                            genResolvedClass(classReader, it.absolutePath)
                        }
                    }

                }
                clazzDir.changedFiles.forEach { f, s ->
                    WinkLog.w("[常量引用替换] changedFiles file=${f},status=${s}")
                }
            }
        }
    }

    /**
     * 将input的文件拷贝到output路径
     */
    private fun afterTransform(transformInvocation: TransformInvocation?) {
        val inputs: MutableCollection<TransformInput>? = transformInvocation?.inputs
        val outputProvider: TransformOutputProvider? = transformInvocation?.outputProvider

        inputs?.forEach {
            //文件夹里面包含的是我们手写的类以及R.class、BuildConfig.class以及R$XXX.class等
            it.directoryInputs.forEach {
                //获取输出目录
                val dest = outputProvider?.getContentLocation(it.name, it.contentTypes, it.scopes, Format.DIRECTORY)
                //将input的目录复制到output指定目录
                FileUtils.copyDirectory(it.file, dest)
                WinkLog.d("[常量引用替换] dest=$dest")
            }

            //对类型为jar文件的input进行遍历
            it.jarInputs.forEach {
                // 重命名输出文件（同目录copyFile会冲突）
                var jarName = it.name
                val md5Name = DigestUtils.md5Hex(it.file.absolutePath)
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length - 4)
                }
                val dest = outputProvider?.getContentLocation(jarName + md5Name, it.contentTypes, it.scopes, Format.JAR)
                FileUtils.copyFile(it.file, dest)
                WinkLog.d("[常量引用替换] dest=$dest")
            }
        }
    }


    private fun printCopyRight() {
        WinkLog.d("####################################")
        WinkLog.d("#######      常量引用替换        #####")
        WinkLog.d("####################################")
    }


    private fun mapConst(classReader: ClassReader) {
        val className = classReader.className;
        classReader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?, value: Any?): FieldVisitor? {
                if (access and Opcodes.ACC_PUBLIC == Opcodes.ACC_PUBLIC && access and Opcodes.ACC_STATIC == Opcodes.ACC_STATIC && access and Opcodes.ACC_FINAL == Opcodes.ACC_FINAL) {
                    name?.let {
                        clazzConstMap.add(ClazzConstMap(className, mapOf(it to value)))
                    }
                }
                return null//super.visitField(access, name, descriptor, signature, value)
            }

            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                return null
            }
        }, ClassReader.EXPAND_FRAMES)
    }

    private fun genResolvedClass(classReader: ClassReader, classPath: String) {

        val classNode = ClassNode()
        classReader.accept(classNode, 0)

        val className = classReader.className
        val resolvedClass = ResolvedClass(className)
        val const2Classes: HashMap<String, HashSet<String>> = loadConst2Classes(project.rootDir.absolutePath)



        classNode.fields.forEach {

            if (it.access and Opcodes.ACC_PUBLIC == Opcodes.ACC_PUBLIC
                && it.access and Opcodes.ACC_STATIC == Opcodes.ACC_STATIC
                && it.access and Opcodes.ACC_FINAL == Opcodes.ACC_FINAL
                && (it.value is Int || it.value is Long || it.value is String || it.value is Double || it.value is Float)
            ) {
                it.name?.let { constName ->
                    resolvedClass.constKV.put(constName, getConstMapKey(it.value))
                }
            }
        }

        val classPath = getClassFullPath(project.projectDir.absolutePath, className, classPath)
        classNode.methods.forEach {

            val iterator = it.instructions.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                when {
                    next is LdcInsnNode -> {//常量
                        if (next.cst != null
                            && (next.cst is Int || next.cst is Long || next.cst is String || next.cst is Double || next.cst is Float)
                        ) {
                            val key = getConstMapKey(next.cst)
                            WinkLog.d("\t $className 中常量对应:$key:$classPath")
                            const2Classes.getOrPut(key) { hashSetOf() }.add(classPath)
                        }
                    }
                }
            }
        }

        saveResovedClass(project.rootDir.absolutePath, resolvedClass)
        saveConst2Classes(project.rootDir.absolutePath, const2Classes)
    }

    /**
     * 2022/8/12 需要区分.kt 与.java
     */
    private fun getClassFullPath(projectDir: String, className: String?, classPath: String): String {
//        return if (File("${projectDir}/src/main/java/${className}.java").exists()) {
//            "${projectDir}/src/main/java/${className}.java"
//        } else {
//            "${projectDir}/src/main/java/${className}.kt"
//        }

        //内部类
        var finalClassName = if (className?.contains("$") == true) {
            className.substring(0, className.indexOf("$"))
        } else {
            className
        }

        return if (classPath.contains("build/tmp/kotlin-classes")) {
            if (finalClassName?.endsWith("Kt") == true) {
                finalClassName.substring(0, finalClassName.indexOf("Kt"))
            }
            "${projectDir}/src/main/java/${finalClassName}.kt"
        } else {
            "${projectDir}/src/main/java/${finalClassName}.java"
        }
    }

    private val clazzConstMap = mutableListOf<ClazzConstMap>()

    companion object {
        @JvmStatic
        fun getConstMapKey(cst: Any): String {
            return when {
                cst is Int -> {
                    "Int_$cst"
                }
                cst is Long -> {
                    "Long_$cst"
                }
                cst is String -> {
                    "String_$cst"
                }
                cst is Double -> {
                    "Double_$cst"
                }
                cst is Float -> {
                    "Float_$cst"
                }
                else -> {
                    "NULL"
                }
            }
        }

        /**
         * 获取给定路径下的所有class文件的常量信息
         */
        @JvmStatic
        fun buildDiffResolvedClass(dir: File): List<ResolvedClass> {

            val rst = mutableListOf<ResolvedClass>()

            dir.walk()
                .filter {
                    it.isFile && it.absolutePath.endsWith(".class")
                }
                .filterNot { classFile ->
                    val index = classFile.absolutePath.lastIndexOf("/")
                    val substring = classFile.absolutePath.substring(index + 1)
                    substring.startsWith("R.class")
                            || substring.startsWith("R$")
                            || substring.startsWith("BuildConfig")
                            || substring.startsWith("ARouter")
                }.map { classFile ->
                    WinkLog.d("[常量引用替换] 获取${classFile.absolutePath}的常量信息:")
                    val classReader = ClassReader(FileInputStream(classFile.absolutePath))
                    val classNode = ClassNode()
                    classReader.accept(classNode, 0)
                    Pair(ResolvedClass(classReader.className), classNode.fields)
                }
                .forEach { (resolvedClass, fields) ->
                    fields
                        .filter { fn ->
                            fn.access and Opcodes.ACC_PUBLIC == Opcodes.ACC_PUBLIC
                                    && fn.access and Opcodes.ACC_STATIC == Opcodes.ACC_STATIC
                                    && fn.access and Opcodes.ACC_FINAL == Opcodes.ACC_FINAL
                                    && (fn.value is Int || fn.value is Long || fn.value is String || fn.value is Double || fn.value is Float)
                        }
                        .forEach { fn ->
                            resolvedClass.constKV[fn.name] = getConstMapKey(fn.value)
                        }

                    rst.add(resolvedClass)
                }

            return rst
        }

        @JvmStatic
        fun deleteConstDir(rootProjectDir: String) {
            FileUtils.deleteDirectory(File("${rootProjectDir}/.idea/${Constant.TAG}/const/"))
        }

        @JvmStatic
        fun saveResovedClass(rootProjectDir: String, resolvedClass: ResolvedClass) {
            val dir = File("${rootProjectDir}/.idea/${Constant.TAG}/const/")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val resolvedClassFileName = resolvedClass.className.replace("/", "_")
            val file = File(dir, resolvedClassFileName)
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()


            LocalCacheUtil.save2File<ResolvedClass>(resolvedClass, file.absolutePath)
        }

        @JvmStatic
        fun loadResolvedClass(rootProjectDir: String, className: String): ResolvedClass? {
            val dirPath = File("${rootProjectDir}/.idea/${Constant.TAG}/const/")
            val resolvedClassFileName = className.replace("/", "_")
            return File(dirPath, resolvedClassFileName).run {
                if (this.exists()) {
                    ObjectInputStream(FileInputStream(this)).readObject() as ResolvedClass
                } else {
                    null
                }
            }
        }

        @JvmStatic
        fun loadConst2Classes(rootProjectDir: String): java.util.HashMap<String, HashSet<String>> {
            val dir = File("${rootProjectDir}/.idea/${Constant.TAG}/const/const2classes")
            if (!dir.exists()) {
                WinkLog.d("[常量引用替换] $dir 不存在,创建文件")
                dir.parentFile.mkdirs()
                dir.createNewFile()
                return HashMap<String, HashSet<String>>()
            } else {
                return LocalCacheUtil.getCache<HashMap<String, HashSet<String>>>(dir.absolutePath)
            }
        }

        @JvmStatic
        fun saveConst2Classes(rootProjectDir: String, const2Classes: HashMap<String, HashSet<String>>) {
            val dir = File("${rootProjectDir}/.idea/${Constant.TAG}/const/const2classes").absolutePath
            LocalCacheUtil.save2File<HashMap<String, HashSet<String>>>(const2Classes, dir)
        }
    }

}

