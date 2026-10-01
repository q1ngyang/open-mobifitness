package org.openmobifitness.core

/** SemVer precedence, including alpha.10 > alpha.2 and stable > prerelease. */
data class Version(val major: Int,val minor: Int,val patch: Int,val pre: List<String>): Comparable<Version> {
    override fun compareTo(other: Version): Int {
        for((a,b) in listOf(major to other.major,minor to other.minor,patch to other.patch)) if(a!=b) return a.compareTo(b)
        if(pre.isEmpty() || other.pre.isEmpty()) return if(pre.isEmpty()==other.pre.isEmpty()) 0 else if(pre.isEmpty()) 1 else -1
        for((a,b) in pre.zip(other.pre)) {
            val x=a.toLongOrNull(); val y=b.toLongOrNull()
            val cmp=when { x!=null && y!=null -> x.compareTo(y); x!=null -> -1; y!=null -> 1; else -> a.compareTo(b) }
            if(cmp!=0) return cmp
        }
        return pre.size.compareTo(other.pre.size)
    }
    companion object {
        fun parse(text: String): Version? = runCatching {
            val s=text.removePrefix("v").removeSuffix("-debug").substringBefore('+')
            require(s.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*)?")))
            val numbers=s.substringBefore('-').split('.').map { it.toInt() }
            Version(numbers[0],numbers[1],numbers[2],if('-' in s) s.substringAfter('-').split('.') else emptyList())
        }.getOrNull()
    }
}
