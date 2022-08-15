package com.immomo.wink.helper

import com.immomo.wink.Settings
import com.immomo.wink.helper.ConstRefReplaceTransform.Companion.buildDiffResolvedClass
import com.immomo.wink.helper.ConstRefReplaceTransform.Companion.loadConst2Classes
import com.immomo.wink.helper.ConstRefReplaceTransform.Companion.loadResolvedClass
import com.immomo.wink.helper.ConstRefReplaceTransform.Companion.saveConst2Classes
import com.immomo.wink.helper.ConstRefReplaceTransform.Companion.saveResovedClass
import com.immomo.wink.util.WinkLog.i
import java.io.File
import java.util.*

class ConstRefReplaceHelper {

    companion object {
        @JvmStatic
        fun checkConstChange(classDir: File, callback: (moduleName: String, constChangedJava: Set<String>, constChangedKotlin: Set<String>) -> Unit) {

            val rootDir = Settings.env.rootDir ?: return

            val constChangedJava: MutableSet<String> = HashSet()
            val constChangedKotlin: MutableSet<String> = HashSet()

            val mapOfConstClasses: HashMap<String, HashSet<String>> = loadConst2Classes(rootDir)
            //从给定的class路径,获取每个class的常量信息
            buildDiffResolvedClass(classDir).forEach { rcNew ->
                val rcOld = loadResolvedClass(rootDir, rcNew.className)
                i("${rcNew.className}类的以下常量发生了变化:")
                rcNew.constKV.forEach { (constName: String, constValue: String) ->
                    val oldValue = rcOld?.constKV?.get(constName)
                    if (constValue != oldValue) {
                        i("\t\t --------------------------------")
                        i("\t\t 常量[$constName]:旧值=$oldValue,新值=$constValue,发生了变化")
                        //删除旧值与类的映射
                        val classList = mapOfConstClasses.remove(oldValue)
                        if (classList == null) {
                            i("\t\t 引用该常量对应的类,列表为空!")
                        } else {
                            //将新值与类的映射,存档
                            //将常量变化对应的类,放入需要编译的列表;//变化常量对应的类:
                            mapOfConstClasses[constValue] = classList
                            //分开Java与kotlin
//                            classList.forEach { path ->
//                                if (path != null) {
//                                    if (path.endsWith(".kt")) {
//                                        constChangedKotlin.add(path)
//                                    } else if (path.endsWith(".java")) {
//                                        constChangedJava.add(path)
//                                    }
//                                }
//                            }
                            classList.groupBy { path ->
                                if (path.endsWith(".kt")) ".kt" else ".java"
                            }.forEach { (k, paths) ->
                                i("\t\t 源文件后缀为=$k,paths=$paths")
                                if (k == ".kt") {
                                    constChangedKotlin.addAll(paths)
                                } else {
                                    constChangedJava.addAll(paths)
                                }
                            }

                            //快照
                            i("\t\t 引用该常量对应的类,如下:")
                            classList.forEach { c -> i("\t\t\t $c") }

                        }
                    }
                }
                i("--------------------------------")
                saveConst2Classes(rootDir, mapOfConstClasses)
                saveResovedClass(rootDir, rcNew)
            }

            callback.invoke("wink-demo-app", constChangedJava, constChangedKotlin)
        }
    }
}