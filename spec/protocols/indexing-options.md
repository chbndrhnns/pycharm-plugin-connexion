Alternative Approaches to Populate the Index
Since we cannot hook into Python's plugin stub infrastructure which would require modifying the Python plugin itself,
here are several
alternative approaches to manage index population:

Option 1: FileBasedIndex Recommended
FileBasedIndex is the most flexible alternative. Unlike StubIndex which relies on stub creation during parsing,
FileBasedIndex allows
you to define your own indexer that processes files directly.

// File: src/main/kotlin/.../index/PyClassMembersFileIndex.kt
class PyClassMembersFileIndex : ScalarIndexExtension<String>() {

       companion object {
           val NAME: ID<String, Void> = ID.create("Py.class.members.file")

           fun findFilesWithMember(memberName: String, project: Project, scope: GlobalSearchScope): Collection<VirtualFile> {
               return FileBasedIndex.getInstance().getContainingFiles(NAME, memberName, scope)
           }
       }

       override fun getName(): ID<String, Void> = NAME

       override fun getIndexer(): DataIndexer<String, Void, FileContent> {
           return DataIndexer { inputData ->
               val result = mutableMapOf<String, Void?>()

               if (inputData.fileType == PythonFileType.INSTANCE) {
                   val psiFile = inputData.psiFile as? PyFile ?: return@DataIndexer result

                   // Index all class members
                   psiFile.topLevelClasses.forEach { pyClass ->
                       pyClass.methods.forEach { method ->
                           method.name?.let { result[it] = null }
                       }
                       pyClass.classAttributes.forEach { attr ->
                           attr.name?.let { result[it] = null }
                       }
                   }
               }
               result
           }
       }

       override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
       override fun getVersion(): Int = 1
       override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(PythonFileType.INSTANCE)
       override fun dependsOnFileContent(): Boolean = true

}

Pros:

• Full control over indexing logic
• Automatically updated when files change
• Can index any PSI element, not just stub-supported ones

Cons:

• Returns VirtualFile not PyClass directly need additional PSI resolution
• Slightly more complex implementation

Option 2: On-Demand PSI Scanning with Aggressive Caching
Keep the current fallback approach but enhance the caching to be more persistent:

object PyClassMembersCache {
// Cache member -> classes mapping at project level
private val projectCache = ConcurrentHashMap<Project, MutableMap<String, MutableSet<String>>>()

       // Track which files have been indexed
       private val indexedFiles = ConcurrentHashMap<Project, MutableSet<String>>()

       fun getClassesWithMember(memberName: String, project: Project, scope: GlobalSearchScope): Collection<PyClass> {
           ensureIndexed(project, scope)

           val classNames = projectCache[project]?.get(memberName) ?: return emptyList()
           return classNames.flatMap { PyClassNameIndex.find(it, project, scope) }
       }

       private fun ensureIndexed(project: Project, scope: GlobalSearchScope) {
           val indexed = indexedFiles.getOrPut(project) { mutableSetOf() }
           val cache = projectCache.getOrPut(project) { mutableMapOf() }

           // Only scan files not yet indexed
           FileTypeIndex.getFiles(PythonFileType.INSTANCE, scope).forEach { vFile ->
               if (vFile.path !in indexed) {
                   indexFile(vFile, project, cache)
                   indexed.add(vFile.path)
               }
           }
       }

       private fun indexFile(vFile: VirtualFile, project: Project, cache: MutableMap<String, MutableSet<String>>) {
           val psiFile = PsiManager.getInstance(project).findFile(vFile) as? PyFile ?: return
           psiFile.topLevelClasses.forEach { pyClass ->
               val className = pyClass.name ?: return@forEach
               pyClass.methods.forEach { method ->
                   method.name?.let { cache.getOrPut(it) { mutableSetOf() }.add(className) }
               }
               pyClass.classAttributes.forEach { attr ->
                   attr.name?.let { cache.getOrPut(it) { mutableSetOf() }.add(className) }
               }
           }
       }

       fun invalidate(project: Project) {
           projectCache.remove(project)
           indexedFiles.remove(project)
       }

}

Pros:

• Simple to implement
• No plugin.xml registration needed
• Works immediately

Cons:

• First search is still slow On scan
• Memory usage grows with project size

Option 3: Use Existing Python Indexes Creatively
Leverage PyClassAttributesIndex which already indexes class attributes:

private fun findCandidateClasses(
project: Project, scope: GlobalSearchScope, memberName: String
): Collection<PyClass> {
val candidates = mutableSetOf<PyClass>()

       // Use PyClassAttributesIndex for attributes (already populated by Python plugin)
       StubIndex.getInstance().processElements(
           PyClassAttributesIndex.KEY,
           memberName,
           project,
           scope,
           PyTargetExpression::class.java
       ) { attr ->
           attr.containingClass?.let { candidates.add(it) }
           true
       }

       // For methods, iterate classes but filter early
       StubIndex.getInstance().processAllKeys(PyClassNameIndex.KEY, project) { className ->
           PyClassNameIndex.find(className, project, scope).forEach { cls ->
               if (cls.findMethodByName(memberName, false, null) != null) {
                   candidates.add(cls)
               }
           }
           true
       }

       return candidates

}

Pros:

• Attributes are already indexed fast lookup
• No new index needed for attributes

Cons:

• Methods still require iteration
• Partial optimization only

Option 4: Background Indexing Service
Create a project service that builds the index in the background:

@Service(Service.Level.PROJECT)
class PyClassMembersIndexService(private val project: Project) : Disposable {

       private val memberToClasses = ConcurrentHashMap<String, MutableSet<String>>()
       private val isIndexed = AtomicBoolean(false)

       init {
           // Start background indexing
           ApplicationManager.getApplication().executeOnPooledThread {
               buildIndex()
           }
       }

       private fun buildIndex() {
           ReadAction.run<Throwable> {
               val scope = GlobalSearchScope.projectScope(project)
               FileTypeIndex.getFiles(PythonFileType.INSTANCE, scope).forEach { vFile ->
                   ProgressManager.checkCanceled()
                   indexFile(vFile)
               }
               isIndexed.set(true)
           }
       }

       fun findClassesWithMember(memberName: String): Set<String> {
           return memberToClasses[memberName] ?: emptySet()
       }

       fun isReady(): Boolean = isIndexed.get()

       override fun dispose() {
           memberToClasses.clear()
       }

}

Register in plugin.xml:

   <projectService serviceImplementation="...PyClassMembersIndexService"/>

Pros:

• Non-blocking initial search
• Index builds in background
• Can show "indexing..." status to user

Cons:

• Results may be incomplete until indexing finishes
• More complex lifecycle management

Recommendation
For your use case, I recommend Option 1 FileBasedIndex combined with the existing cache:

1. FileBasedIndex provides automatic index updates when files change
2. Cache provides sub-millisecond repeated lookups
3. Fallback to current iteration if index is empty backward compatibility

This gives you the best balance of performance, correctness, and maintainability. The FileBasedIndex is the standard
IntelliJ way to
create custom indexes from plugins without modifying core plugin infrastructure.