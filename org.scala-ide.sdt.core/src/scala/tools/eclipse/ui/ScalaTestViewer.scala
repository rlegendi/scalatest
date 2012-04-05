package scala.tools.eclipse.ui

import org.eclipse.ui.part.PageBook
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.StyledCellLabelProvider
import java.text.NumberFormat
import org.eclipse.jface.viewers.StyledString
import java.text.MessageFormat
import org.eclipse.swt.SWT
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.viewers.LabelProviderChangedEvent
import org.eclipse.swt.widgets.Control
import org.eclipse.jface.viewers.StructuredViewer
import org.eclipse.jface.action.Action
import scala.tools.eclipse.ScalaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.jdt.internal.ui.viewsupport.SelectionProviderMediator
import org.eclipse.jface.viewers.IStructuredSelection

class ScalaTestViewer(parent: Composite, fTestRunnerPart: ScalaTestRunnerViewPart) {

  private var fViewerbook: PageBook = null
  private var fTreeViewer: TreeViewer = null
  private var fTreeContentProvider: TestSessionTreeContentProvider = null
  private var fTreeLabelProvider: TestSessionLabelProvider = null
  private var fSelectionProvider: SelectionProviderMediator = null
  
  private var fTreeNeedsRefresh = false
  private var fNeedUpdate: Set[Node] = null
  private var fAutoScrollTarget: Node = null
  
  private var fAutoClose: List[Node] = null
  private var fAutoExpand: Set[Node] = null
  
  private var fLayoutMode: Int = ScalaTestRunnerViewPart.LAYOUT_HIERARCHICAL
  
  createTestViewers(parent)

  registerViewersRefresh()

  //initContextMenu()
  
  private def createTestViewers(parent: Composite) {
    fViewerbook = new PageBook(parent, SWT.NULL);

    fTreeViewer = new TreeViewer(fViewerbook, SWT.V_SCROLL | SWT.SINGLE);
    fTreeViewer.setUseHashlookup(true);
    fTreeContentProvider = new TestSessionTreeContentProvider();
    fTreeViewer.setContentProvider(fTreeContentProvider);
	fTreeLabelProvider = new TestSessionLabelProvider(fTestRunnerPart, ScalaTestRunnerViewPart.LAYOUT_HIERARCHICAL);
	fTreeViewer.setLabelProvider(new ColoringLabelProvider(fTreeLabelProvider));

    /*fTableViewer = new TableViewer(fViewerbook, SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE);
    fTableViewer.setUseHashlookup(true);
    fTableContentProvider= new TestSessionTableContentProvider();
    fTableViewer.setContentProvider(fTableContentProvider);
    fTableLabelProvider= new TestSessionLabelProvider(fTestRunnerPart, TestRunnerViewPart.LAYOUT_FLAT);
    fTableViewer.setLabelProvider(new ColoringLabelProvider(fTableLabelProvider));*/

    //fSelectionProvider= new SelectionProviderMediator(new StructuredViewer[] { fTreeViewer, fTableViewer }, fTreeViewer);
	fSelectionProvider= new SelectionProviderMediator(Array[StructuredViewer](fTreeViewer), fTreeViewer)
    //fSelectionProvider.addSelectionChangedListener(new TestSelectionListener());
    val openSourceCodeListener= new OpenSourceCodeListener(fTestRunnerPart, fSelectionProvider)
    fTreeViewer.getTree().addSelectionListener(openSourceCodeListener)
    //fTableViewer.getTable().addSelectionListener(testOpenListener);

    //fTestRunnerPart.getSite().setSelectionProvider(fSelectionProvider);

    fViewerbook.showPage(fTreeViewer.getTree());
  }
  
  def registerViewersRefresh() {
	synchronized {
      fTreeNeedsRefresh= true
      //fTableNeedsRefresh= true
      clearUpdateAndExpansion()
    }
  }
  
  private def clearUpdateAndExpansion() {
    fNeedUpdate = Set[Node]()
    fAutoClose = List[Node]()
    fAutoExpand = Set[Node]()
  }
  
  def registerTestAdded(node: Node) {
    synchronized {
      //TODO: performance: would only need to refresh parent of added element
      fTreeNeedsRefresh= true
      //fTableNeedsRefresh= true;
    }
  }

  def registerViewerUpdate(node: Node) {
    synchronized {
      fNeedUpdate += node
    }
  }

  private def clearAutoExpand() {
    synchronized {
      fAutoExpand = Set.empty[Node]
    }
  }

  def registerAutoScrollTarget(node: Node) {
    fAutoScrollTarget = node;
  }

  def registerFailedForAutoScroll(node: Node) {
    synchronized {
      val parent = fTreeContentProvider.getParent(node)
      if (parent != null)
        fAutoExpand += parent.asInstanceOf[Node]
    }
  }

  def expandFirstLevel() {
    fTreeViewer.expandToLevel(2)
  }
  
  def getTestViewerControl: Control = fViewerbook
  
  private def getActiveViewer(): StructuredViewer = {
    //if (fLayoutMode == ScalaTestRunnerViewPart.LAYOUT_HIERARCHICAL)
      return fTreeViewer;
    //else
      //return fTableViewer;
  }
  
  private def getActiveViewerNeedsRefresh: Boolean = {
    //if (fLayoutMode == ScalaTestRunnerViewPart.LAYOUT_HIERARCHICAL)
      return fTreeNeedsRefresh;
    //else
      //return fTableNeedsRefresh;
  }

  private def setActiveViewerNeedsRefresh(needsRefresh: Boolean) {
    //if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL)
      fTreeNeedsRefresh= needsRefresh;
    //else
      //fTableNeedsRefresh= needsRefresh;
  }
  
  def processChangesInUI() {
    if (fTestRunnerPart.fTestRunSession == null) {
      registerViewersRefresh()
      fTreeNeedsRefresh= false
      //fTableNeedsRefresh= false
      fTreeViewer.setInput(null)
      //fTableViewer.setInput(null)
      return
    }

    val testRoot = fTestRunnerPart.fTestRunSession.rootNode

    val viewer = getActiveViewer()
    if (getActiveViewerNeedsRefresh) {
      clearUpdateAndExpansion()
      setActiveViewerNeedsRefresh(false)
      viewer.setInput(testRoot)
    } 
    else {
      var toUpdate: Array[AnyRef] = null
      synchronized {
        toUpdate= fNeedUpdate.toArray
        fNeedUpdate = Set.empty[Node]
      }
      /*if (!fTreeNeedsRefresh && toUpdate.length > 0) {
        if (fTreeHasFilter)
          for (Object element : toUpdate)
            updateElementInTree((TestElement) element);
        else {
          var toUpdateWithParents = Set[AnyRef]()
          toUpdateWithParents ++= toUpdate
          for (element <- toUpdate) {
            var parent= element.asInstanceOf[Node].parent
            while (parent != null) {
              toUpdateWithParents += parent
              parent = parent.parent
            }
          }
          val nullStringArray: Array[String] = null
          val toUpdateWithParentsArray: Array[Any] = toUpdateWithParents.toArray
          println("#####to update with parents: " + toUpdateWithParentsArray.length)
          fTreeViewer.update(toUpdateWithParentsArray, nullStringArray)
        }
      }
      if (! fTableNeedsRefresh && toUpdate.length > 0) {
          if (fTableHasFilter)
            for (Object element : toUpdate)
              updateElementInTable((TestElement) element);
          else
            fTableViewer.update(toUpdate, null);
      }*/
      // TODO: The above code to update node selectively doesn't work, use the full refresh for now.
      fTreeViewer.refresh()
    }
    autoScrollInUI();
  }
  
  private def autoScrollInUI() {
    if (!fTestRunnerPart.autoScroll) {
      clearAutoExpand()
      fAutoClose = List[Node]()
      return
    }

    /*if (fLayoutMode == ScalaTestRunnerViewPart.LAYOUT_FLAT) {
      if (fAutoScrollTarget != null)
        fTableViewer.reveal(fAutoScrollTarget);
        return;
    }*/

    synchronized {
      for (node <- fAutoExpand) {
        fTreeViewer.setExpandedState(node, true)
      }
      clearAutoExpand()
    }

	val current = fAutoScrollTarget
    fAutoScrollTarget = null

    // Not sure what's the following is doing, TODO later.
    /*TestSuiteElement parent= current == null ? null : (TestSuiteElement) fTreeContentProvider.getParent(current);
    if (fAutoClose.isEmpty() || ! fAutoClose.getLast().equals(parent)) {
      // we're in a new branch, so let's close old OK branches:
      for (ListIterator<TestSuiteElement> iter= fAutoClose.listIterator(fAutoClose.size()); iter.hasPrevious();) {
        TestSuiteElement previousAutoOpened= iter.previous()
        if (previousAutoOpened.equals(parent))
          break;

        if (previousAutoOpened.getStatus() == TestElement.Status.OK) {
          // auto-opened the element, and all children are OK -> auto close
          iter.remove();
          fTreeViewer.collapseToLevel(previousAutoOpened, AbstractTreeViewer.ALL_LEVELS);
        }
      }

      while (parent != null && ! fTestRunSession.getTestRoot().equals(parent) && fTreeViewer.getExpandedState(parent) == false) {
        fAutoClose.add(parent); // add to auto-opened elements -> close later if STATUS_OK
        parent= (TestSuiteElement) fTreeContentProvider.getParent(parent);
      }
    }*/
    if (current != null)
      fTreeViewer.reveal(current);
  }
}

private class TestSessionTreeContentProvider extends ITreeContentProvider {
  
  private val NO_CHILDREN = Array.empty[AnyRef]
  
  def dispose() {
  }
  
  def getChildren(parentElement: AnyRef): Array[AnyRef] = {
    Array.empty[AnyRef] ++ parentElement.asInstanceOf[Node].children
  }
  
  def getElements(inputElement: AnyRef): Array[AnyRef] = {
    Array.empty[AnyRef] ++ inputElement.asInstanceOf[RunModel].children
  }

  def getParent(element: AnyRef): AnyRef = {
    element.asInstanceOf[Node].parent
  }

  def hasChildren(element: AnyRef): Boolean = {
    element.asInstanceOf[Node].hasChildren
  }

  def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef) {
  }
}

private class TestSessionLabelProvider(fTestRunnerPart: ScalaTestRunnerViewPart, fLayoutMode: Int) extends LabelProvider with IStyledLabelProvider {

  private var fShowTime = true
  private val timeFormat: NumberFormat = NumberFormat.getNumberInstance()
  
  timeFormat.setGroupingUsed(true)
  timeFormat.setMinimumFractionDigits(3)
  timeFormat.setMaximumFractionDigits(3)
  timeFormat.setMinimumIntegerDigits(1)
  
  def getStyledText(element: AnyRef): StyledString = {
    val label= getSimpleLabel(element)
    if (label == null) {
      return new StyledString(element.toString())
    }
    val text = new StyledString(label)

    val duration = 
      element match {
        case test: TestModel => test.duration
        case scope: ScopeModel => None
        case suite: SuiteModel => suite.duration
        case run: RunModel => run.duration
        case info: InfoModel => None
        case markup: MarkupModel => None
      }
    return addElapsedTime(text, duration)
  }
  
  private def addElapsedTime(styledString: StyledString, time: Option[Long]): StyledString = {
    val string = styledString.getString()
    val decorated= addElapsedTime(string, time)
	StyledCellLabelProvider.styleDecoratedString(decorated, StyledString.COUNTER_STYLER, styledString);
  }

  private def addElapsedTime(string: String, time: Option[Long]): String = {
    time match {
      case Some(time) => 
        val seconds = time / 1000.0
        if (!fShowTime || seconds == Double.NaN)
          string
        else {
          val formattedTime = timeFormat.format(seconds);
          string + " (" + formattedTime + " s)"
        }
      case None => 
        string
    }
  }
  
  private def getSimpleLabel(element: AnyRef): String = {
    element match {
      case test: TestModel => test.testText
      case scope: ScopeModel => scope.message
      case suite: SuiteModel => suite.suiteName
      case run: RunModel => "Run"
      case info: InfoModel => info.message
      case markup: MarkupModel => markup.text
      case _ => element.toString
    }
  }
  
  override def getText(element: AnyRef): String = {
    val label = getSimpleLabel(element);
    if (label == null) {
      return element.toString();
    }
    val duration = 
      element match {
        case test: TestModel => test.duration
        case scope: ScopeModel => None
        case suite: SuiteModel => suite.duration
        case run: RunModel => run.duration
        case info: InfoModel => None
        case markup: MarkupModel => None
      }
    return addElapsedTime(label, duration)
  }
  
  override def getImage(element: AnyRef): Image = {
    element match {
      case test: TestModel => 
        test.status match {
          case TestStatus.STARTED =>
            fTestRunnerPart.testRunIcon
          case TestStatus.SUCCEEDED =>
            fTestRunnerPart.testSucceedIcon
          case TestStatus.FAILED => 
            fTestRunnerPart.testFailedIcon
          case TestStatus.IGNORED => 
            fTestRunnerPart.testIgnoredIcon
          case TestStatus.PENDING => 
            fTestRunnerPart.testIgnoredIcon
          case TestStatus.CANCELED =>
            fTestRunnerPart.testIgnoredIcon
        }
      case scope: ScopeModel => 
        fTestRunnerPart.scopeIcon
      case suite: SuiteModel => 
        suite.status match {
          case SuiteStatus.STARTED => 
            fTestRunnerPart.suiteRunIcon
          case SuiteStatus.SUCCEED =>
            fTestRunnerPart.suiteSucceedIcon
          case SuiteStatus.FAILED => 
            fTestRunnerPart.suiteFailIcon
          case SuiteStatus.ABORTED =>
            fTestRunnerPart.suiteAbortedIcon
        }
      case info: InfoModel => 
        fTestRunnerPart.infoIcon
      case markup: MarkupModel => 
        fTestRunnerPart.markupIcon
      case _ => 
        throw new IllegalArgumentException(String.valueOf(element))
    }
  }

  def setShowTime(showTime: Boolean) {
    fShowTime = showTime
    fireLabelProviderChanged(new LabelProviderChangedEvent(this));
  }
}

private class OpenSourceCodeListener(fTestRunnerPart: ScalaTestRunnerViewPart, fSelectionProvider: SelectionProviderMediator) extends SelectionAdapter {
  override def widgetDefaultSelected(e: SelectionEvent) {
    val selection= fSelectionProvider.getSelection().asInstanceOf[IStructuredSelection]
    if (selection.size() != 1)
      return
      
    val node = selection.getFirstElement.asInstanceOf[Node]
    val action = new GoToSourceAction(node, fTestRunnerPart)
    if (action.isEnabled)
      action.run()

		/*TestElement testElement= (TestElement) selection.getFirstElement();

		OpenTestAction action;
		if (testElement instanceof TestSuiteElement) {
			action= new OpenTestAction(fTestRunnerPart, testElement.getTestName());
		} else if (testElement instanceof TestCaseElement){
			TestCaseElement testCase= (TestCaseElement) testElement;
			action= new OpenTestAction(fTestRunnerPart, testCase);
		} else {
			throw new IllegalStateException(String.valueOf(testElement));
		}

		if (action.isEnabled())
			action.run();*/
  }
}

private class GoToSourceAction(node: Node, fTestRunnerPart: ScalaTestRunnerViewPart) extends Action {
  
  override def run() {
    node match {
      case test: TestModel => 
        
      case suite: SuiteModel =>
        goToLocation(suite.location)
      case _ =>
        
    }
  }
  
  private def goToLocation(location: Option[Location]) {
    println("#####location: " + location)
    location match {
      case Some(location) =>
        location match {
          case topOfClass: TopOfClass => 
            val scProj = getScalaProject(fTestRunnerPart.fTestRunSession.projectName)
            scProj match {
              case Some(scProj) => 
                val iType = scProj.javaProject.findType(topOfClass.className)
                if (iType != null)
                  JavaUI.openInEditor(iType, true, true)
              case None =>
                
            }
          case topOfMethod: TopOfMethod => 
        
          case lineInFile: LineInFile =>
        
          case SeeStackDepthException =>
        }  
      case None =>
      
    }
  }
  
  private def getScalaProject(projectName: String): Option[ScalaProject] = {
    val model = JavaCore.create(ResourcesPlugin.getWorkspace.getRoot)
    val javaProject = model.getJavaProject(projectName)
    if (javaProject != null)
      Some(ScalaProject(javaProject.getProject))
    else
      None
  }
}