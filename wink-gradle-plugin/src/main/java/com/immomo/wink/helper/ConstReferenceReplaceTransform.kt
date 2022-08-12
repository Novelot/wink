package com.immomo.wink.helper

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.immomo.wink.Constant
import com.immomo.wink.ResolvedClass
import com.immomo.wink.const_ref.ClazzConstMap
import com.immomo.wink.util.LocalCacheUtil
import com.immomo.wink.util.WinkLog
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.Project
import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.io.*
import org.apache.commons.io.FileUtils

/**
 * 常量引用替换优化问题
 * @author 刘云龙
 */
class ConstReferenceReplaceTransform(val project: Project) : Transform() {

    override fun getName(): String = "ConstReferenceReplaceTransform"

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
    }

    private fun onTransform(transformInvocation: TransformInvocation?) {
        transformInvocation?.inputs?.forEach { input ->
            WinkLog.d("[ConstReferenceReplaceTransform] ---------------")
            WinkLog.d("[ConstReferenceReplaceTransform] intput=${input}")

            input.directoryInputs.forEach { clazzDir ->
                WinkLog.d("[ConstReferenceReplaceTransform] dirInput=${clazzDir}")
                clazzDir.file.walkBottomUp().forEach {
                    //WinkLog.vNoLimit("[ConstReferenceReplaceTransform] walk.file=${it}")
                    if (it.isFile && it.absolutePath.endsWith(".class")) {
                        //WinkLog.vNoLimit("[ConstReferenceReplaceTransform] walk.file=${it.absolutePath}")
                        val index = it.absolutePath.indexOfLast { it == '/' }
                        val substring = it.absolutePath.substring(index + 1)
                        //WinkLog.vNoLimit("[ConstReferenceReplaceTransform] index=${index}, substring=${substring}")
                        if (!substring.startsWith("R.class")
                            && !substring.startsWith("R$")
                            && !substring.startsWith("BuildConfig")
                            && !substring.startsWith("ARouter")
                        ) {
                            //WinkLog.vNoLimit("[ConstReferenceReplaceTransform] walk.file=${it.absolutePath}")

                            val classReader = ClassReader(FileInputStream(it.absolutePath))
                            genResolvedClass(classReader)
                        }
                    }

                }
                clazzDir.changedFiles.forEach { f, s ->
                    WinkLog.w("[Novelot] changedFiles file=${f},status=${s}")
                }
            }
        }
    }

//    override fun transform(context: Context?, inputs: MutableCollection<TransformInput>?, referencedInputs: MutableCollection<TransformInput>?, outputProvider: TransformOutputProvider?, isIncremental: Boolean) {
//        super.transform(context, inputs, referencedInputs, outputProvider, isIncremental)
//
//        // Transform的inputs有两种类型，一种是目录，一种是jar包，要分开遍历
//        inputs?.forEach {
//            //文件夹里面包含的是我们手写的类以及R.class、BuildConfig.class以及R$XXX.class等
//            it.directoryInputs.forEach {
//                //获取输出目录
//                val dest = outputProvider?.getContentLocation(it.name, it.contentTypes, it.scopes, Format.DIRECTORY)
//                //将input的目录复制到output指定目录
//                FileUtils.copyDirectory(it.file, dest)
//            }
//
//            //对类型为jar文件的input进行遍历
//            it.jarInputs.forEach {
//                // 重命名输出文件（同目录copyFile会冲突）
//                var jarName = it.name
//                val md5Name = DigestUtils.md5Hex(it.file.absolutePath)
//                if (jarName.endsWith(".jar")) {
//                    jarName = jarName.substring(0, jarName.length - 4)
//                }
//                val dest = outputProvider?.getContentLocation(jarName + md5Name, it.contentTypes, it.scopes, Format.JAR)
//                FileUtils.copyFile(it.file, dest)
//            }
//        }
//    }

    /**
     * 将input的文件拷贝到output路径
     */
    private fun afterTransform(transformInvocation: TransformInvocation?) {
//        transformInvocation?.outputProvider?.deleteAll()
//        transformInvocation?.inputs?.forEach {
//            it.directoryInputs.forEach { input ->
//                val dir = input.file
//                val dest = transformInvocation?.outputProvider?.getContentLocation(input.name, input.contentTypes, input.scopes, Format.DIRECTORY)
//                val srcDirPath = dir?.absolutePath
//                val destDirPath = dest?.absolutePath
//                FileUtils.copyDirectory(dir, dest)
//            }
//        }

        val inputs: MutableCollection<TransformInput>? = transformInvocation?.inputs
        val outputProvider: TransformOutputProvider? = transformInvocation?.outputProvider

        inputs?.forEach {
            //文件夹里面包含的是我们手写的类以及R.class、BuildConfig.class以及R$XXX.class等
            it.directoryInputs.forEach {
                //获取输出目录
                val dest = outputProvider?.getContentLocation(it.name, it.contentTypes, it.scopes, Format.DIRECTORY)
                //将input的目录复制到output指定目录
                FileUtils.copyDirectory(it.file, dest)
                WinkLog.d("Novelot", "dest=$dest")
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
                WinkLog.d("Novelot", "dest=$dest")
            }
        }
    }


    private fun printCopyRight() {
        WinkLog.d("####################################")
        WinkLog.d("#######      常量引用替换        #####")
        WinkLog.d("#######                        #####")
        WinkLog.d("#######      月落乌啼霜满天，    #####")
        WinkLog.d("#######      江枫渔火对愁眠。    #####")
        WinkLog.d("#######      姑苏城外寒山寺，    #####")
        WinkLog.d("#######      夜半钟声到客船。    #####")
        WinkLog.d("####################################")
    }


    private fun mapConst(classReader: ClassReader) {
        val className = classReader.className;
        classReader.accept(object : ClassVisitor(Opcodes.ASM9) {

            //                                    override fun visitSource(source: String?, debug: String?) {
            //                                        super.visitSource(source, debug)
            //                                        WinkLog.vNoLimit("[ConstReferenceReplaceTransform] visitSource  source=${source},debug=${debug}")
            //                                    }

            //                                    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
            //                                        val visitMethod = super.visitMethod(access, name, descriptor, signature, exceptions)
            //                                        return visitMethod
            //                                    }
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

    private fun genResolvedClass(classReader: ClassReader) {

        val classNode = ClassNode()
        classReader.accept(classNode, 0)

        val className = classReader.className
        val resolvedClass = ResolvedClass(className)
        val const2Classes: HashMap<String, ArrayList<String>> = loadConst2Classes(project.rootDir.absolutePath)

        classNode.fields.forEach {
            WinkLog.vNoLimit("[Novelot] classNode.fields name=${it.name},value=${it.value}")

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

        classNode.methods.forEach {
            WinkLog.vNoLimit("[ConstReferenceReplaceTransform] methods.forEach :method name=${it.name},desc=${it.desc}")

            val iterator = it.instructions.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()

                //WinkLog.vNoLimit("[ConstReferenceReplaceTransform] methods.forEach iterator :next name=${next}")

                when {
                    next is LdcInsnNode -> {//常量
                        //WinkLog.vNoLimit("[ConstReferenceReplaceTransform] LdcInsnNode cst=${next.cst},type=${next.type}")
                        if (next.cst != null
                            && (next.cst is Int || next.cst is Long || next.cst is String || next.cst is Double || next.cst is Float)
                        ) {
                            val key = getConstMapKey(next.cst)
                            val element = getClassFullPath(project.projectDir.absolutePath, className)
                            WinkLog.i("[Novelot]", "\t $className 中常量对应:$key:$element")
                            if (const2Classes[key] == null) {
                                const2Classes[key] = arrayListOf()
                            }
                            const2Classes[key]?.add(element)
                        }
                    }
                }
            }

        }

//        resolvedClass.constKV.forEach {
//            WinkLog.d("[Novelot] clazzConstMap class=${resolvedClass.className}, constName=${it.key},constValue=${it.value}")
//        }
//
//        const2Classes.forEach {
//            WinkLog.d("[ConstReferenceReplaceTransform] constClazzMap value=${it},class=${resolvedClass.className}")
//        }


        saveResovedClass(project.rootDir.absolutePath, resolvedClass)
        saveConst2Classes(project.rootDir.absolutePath, const2Classes)

    }

    // TODO: 2022/8/12 刘云龙 强制命名为了.java文件,需要区分.kt
    private fun getClassFullPath(projectDir: String, className: String?): String {
        return "${projectDir}/src/main/java/${className}.java"
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
                    WinkLog.d("获取${classFile.absolutePath}的常量信息:")
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
        fun loadConst2Classes(rootProjectDir: String): java.util.HashMap<String, java.util.ArrayList<String>> {
            val dir = File("${rootProjectDir}/.idea/${Constant.TAG}/const/const2classes")
            if (!dir.exists()) {
                WinkLog.i("[Novelot]", "$dir 不存在,创建文件")
                dir.parentFile.mkdirs()
                dir.createNewFile()
                return HashMap<String, ArrayList<String>>()
            } else {
                return LocalCacheUtil.getCache<HashMap<String, ArrayList<String>>>(dir.absolutePath)
            }
        }

        @JvmStatic
        fun saveConst2Classes(rootProjectDir: String, const2Classes: HashMap<String, ArrayList<String>>) {
            val dir = File("${rootProjectDir}/.idea/${Constant.TAG}/const/const2classes").absolutePath
            LocalCacheUtil.save2File<HashMap<String, ArrayList<String>>>(const2Classes, dir)
        }
    }

}

