/*
 * ====================================================================
 * This software is subject to the terms of the Common Public License
 * Agreement, available at the following URL:
 *   http://www.opensource.org/licenses/cpl.html .
 * You must accept the terms of that agreement to use this software.
 * ====================================================================
 */
package com.eyeq.pivot4j.ui.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.olap4j.Axis;
import org.olap4j.Cell;
import org.olap4j.CellSetAxis;
import org.olap4j.Position;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;

import com.eyeq.pivot4j.PivotModel;
import com.eyeq.pivot4j.ui.CellType;
import com.eyeq.pivot4j.ui.PivotRenderer;
import com.eyeq.pivot4j.ui.RenderContext;
import com.eyeq.pivot4j.ui.RenderStrategy;
import com.eyeq.pivot4j.util.TreeNode;
import com.eyeq.pivot4j.util.TreeNodeCallback;

public class RendererStrategyImpl implements RenderStrategy {

	private boolean hideSpans = false;

	private boolean showParentMembers = false;

	private boolean showDimensionTitle = true;

	/**
	 * @return the hideSpans
	 * @see com.eyeq.pivot4j.ui.RenderStrategy#getHideSpans()
	 */
	public boolean getHideSpans() {
		return hideSpans;
	}

	/**
	 * @param hideSpans
	 *            the hideSpans to set
	 * @see com.eyeq.pivot4j.ui.RenderStrategy#setHideSpans(boolean)
	 */
	public void setHideSpans(boolean hideSpans) {
		this.hideSpans = hideSpans;
	}

	/**
	 * @return the showParentMembers
	 * @see com.eyeq.pivot4j.ui.RenderStrategy#getShowParentMembers()
	 */
	public boolean getShowParentMembers() {
		return showParentMembers;
	}

	/**
	 * @param showParentMembers
	 *            the showParentMembers to set
	 * @see com.eyeq.pivot4j.ui.RenderStrategy#setShowParentMembers(boolean)
	 */
	public void setShowParentMembers(boolean showParentMembers) {
		this.showParentMembers = showParentMembers;
	}

	/**
	 * @return the showDimensionTitle
	 * @see com.eyeq.pivot4j.ui.RenderStrategy#getShowDimensionTitle()
	 */
	public boolean getShowDimensionTitle() {
		return showDimensionTitle;
	}

	/**
	 * @param showDimensionTitle
	 *            the showDimensionTitle to set
	 * @see com.eyeq.pivot4j.ui.RenderStrategy#setShowDimensionTitle(boolean)
	 */
	public void setShowDimensionTitle(boolean showDimensionTitle) {
		this.showDimensionTitle = showDimensionTitle;
	}

	/**
	 * @param model
	 * @param renderer
	 * @see com.eyeq.pivot4j.ui.RenderStrategy#render(com.eyeq.pivot4j.PivotModel,
	 *      com.eyeq.pivot4j.ui.PivotRenderer)
	 */
	public void render(PivotModel model, PivotRenderer renderer) {
		if (model == null) {
			throw new IllegalArgumentException(
					"Missing required argument 'model'.");
		}

		if (renderer == null) {
			throw new IllegalArgumentException(
					"Missing required argument 'renderer'.");
		}

		List<CellSetAxis> axes = model.getCellSet().getAxes();
		if (axes.isEmpty()) {
			return;
		}

		TableHeaderNode columnRoot = createAxisTree(model, Axis.COLUMNS);
		if (columnRoot == null) {
			return;
		}

		TableHeaderNode rowRoot = createAxisTree(model, Axis.ROWS);
		if (rowRoot == null) {
			return;
		}

		configureAxisTree(model, Axis.COLUMNS, columnRoot);
		configureAxisTree(model, Axis.ROWS, rowRoot);

		freezeAxisTree(model, Axis.COLUMNS, columnRoot);
		freezeAxisTree(model, Axis.ROWS, rowRoot);

		RenderContext context = createRenderContext(model, columnRoot, rowRoot);

		renderer.startTable(context);

		renderHeader(context, columnRoot, rowRoot, renderer);
		renderBody(context, columnRoot, rowRoot, renderer);

		renderer.endTable(context);
	}

	/**
	 * @param model
	 * @param columnRoot
	 * @param rowRoot
	 * @return
	 */
	protected RenderContext createRenderContext(PivotModel model,
			TableHeaderNode columnRoot, TableHeaderNode rowRoot) {
		int columnHeaderCount = columnRoot.getMaxRowIndex();
		int rowHeaderCount = rowRoot.getMaxRowIndex();

		int columnCount = columnRoot.getWidth();
		int rowCount = rowRoot.getWidth();

		return new RenderContext(model, columnCount, rowCount,
				columnHeaderCount, rowHeaderCount);
	}

	/**
	 * @param context
	 * @param columnRoot
	 * @param rowRoot
	 * @param callback
	 */
	protected void renderHeader(final RenderContext context,
			final TableHeaderNode columnRoot, final TableHeaderNode rowRoot,
			final PivotRenderer callback) {

		callback.startHeader(context);

		int count = context.getColumnHeaderCount();

		for (int rowIndex = 0; rowIndex < count; rowIndex++) {
			context.setAxis(Axis.COLUMNS);
			context.setColIndex(0);
			context.setRowIndex(rowIndex);

			callback.startRow(context);

			renderHeaderCorner(context, columnRoot, rowRoot, callback);

			// invoking renderHeaderCorner method resets the axis property.
			context.setAxis(Axis.COLUMNS);

			columnRoot.walkChildrenAtRowIndex(
					new TreeNodeCallback<TableAxisContext>() {

						@Override
						public int handleTreeNode(
								TreeNode<TableAxisContext> node) {
							TableHeaderNode headerNode = (TableHeaderNode) node;

							context.setColIndex(headerNode.getColIndex()
									+ context.getRowHeaderCount());
							context.setColSpan(headerNode.getColSpan());
							context.setRowSpan(headerNode.getRowSpan());

							context.setMember(headerNode.getMember());
							context.setHierarchy(headerNode.getHierarchy());
							context.setColumnPosition(headerNode.getPosition());

							if (context.getMember() == null) {
								if (context.getHierarchy() == null) {
									context.setCellType(CellType.None);
								} else {
									context.setCellType(CellType.ColumnTitle);
								}
							} else {
								context.setCellType(CellType.ColumnHeader);
							}

							callback.startCell(context);
							callback.cellContent(context);
							callback.endCell(context);

							return TreeNodeCallback.CONTINUE;
						}
					}, rowIndex + 1);

			callback.endRow(context);
		}

		callback.endHeader(context);
	}

	/**
	 * @param context
	 * @param rowRoot
	 * @param columnRoot
	 * @param callback
	 */
	protected void renderBody(final RenderContext context,
			final TableHeaderNode columnRoot, final TableHeaderNode rowRoot,
			final PivotRenderer callback) {
		callback.startBody(context);

		int count = rowRoot.getColSpan();

		for (int rowIndex = 0; rowIndex < count; rowIndex++) {
			context.setAxis(Axis.ROWS);
			context.setColIndex(0);
			context.setRowIndex(rowIndex + context.getColumnHeaderCount());

			callback.startRow(context);

			rowRoot.walkChildrenAtColIndex(
					new TreeNodeCallback<TableAxisContext>() {

						@Override
						public int handleTreeNode(
								TreeNode<TableAxisContext> node) {
							TableHeaderNode headerNode = (TableHeaderNode) node;

							if (headerNode.getRowIndex() == 0) {
								return TreeNodeCallback.CONTINUE;
							}

							context.setColIndex(headerNode.getRowIndex() - 1);
							context.setColSpan(headerNode.getRowSpan());
							context.setRowSpan(headerNode.getColSpan());

							context.setMember(headerNode.getMember());
							context.setHierarchy(headerNode.getHierarchy());
							context.setRowPosition(headerNode.getPosition());

							if (context.getMember() == null) {
								if (context.getHierarchy() == null) {
									context.setCellType(CellType.None);
								} else {
									context.setCellType(CellType.RowTitle);
								}
							} else {
								context.setCellType(CellType.RowHeader);
							}

							callback.startCell(context);
							callback.cellContent(context);
							callback.endCell(context);

							if (headerNode.getChildCount() == 0) {
								renderDataRow(context, columnRoot, rowRoot,
										(TableHeaderNode) node, callback);
							}

							return TreeNodeCallback.CONTINUE;
						}
					}, rowIndex);

			callback.endRow(context);
		}

		callback.endBody(context);
	}

	/**
	 * @param context
	 * @param columnRoot
	 * @param rowRoot
	 * @param rowNode
	 * @param callback
	 */
	protected void renderDataRow(RenderContext context,
			TableHeaderNode columnRoot, TableHeaderNode rowRoot,
			TableHeaderNode rowNode, PivotRenderer callback) {
		context.setCellType(CellType.Value);

		for (int i = 0; i < context.getColumnCount(); i++) {
			Cell cell = null;

			TableHeaderNode columnNode = columnRoot.getLeafNodeAtColIndex(i);

			if (columnNode != null && columnNode.getPosition() != null) {
				cell = context.getCellSet().getCell(columnNode.getPosition(),
						rowNode.getPosition());
			}

			context.setCell(cell);
			context.setHierarchy(null);
			context.setMember(null);
			context.setAxis(null);
			context.setColIndex(context.getRowHeaderCount() + i);
			context.setColSpan(1);
			context.setRowSpan(1);

			callback.startCell(context);
			callback.cellContent(context);
			callback.endCell(context);
		}
	}

	/**
	 * @param context
	 * @param columnRoot
	 * @param rowRoot
	 * @param callback
	 */
	protected void renderHeaderCorner(RenderContext context,
			TableHeaderNode columnRoot, TableHeaderNode rowRoot,
			PivotRenderer callback) {
		int offset = 0;

		if (showDimensionTitle) {
			offset = showParentMembers ? 2 : 1;
		}

		context.setAxis(null);

		context.setHierarchy(null);
		context.setLevel(null);
		context.setMember(null);

		context.setCell(null);
		context.setCellType(CellType.None);

		context.setColumnPosition(null);
		context.setRowPosition(null);

		boolean renderDimensionTitle = showDimensionTitle
				&& (context.getRowIndex() == context.getColumnHeaderCount()
						- offset);
		boolean renderLevelTitle = showDimensionTitle
				&& showParentMembers
				&& (context.getRowIndex() == context.getColumnHeaderCount() - 1);

		if (context.getRowIndex() == 0 && !renderDimensionTitle
				&& !renderLevelTitle) {
			context.setColSpan(context.getRowHeaderCount());
			context.setRowSpan(context.getColumnHeaderCount() - offset);

			callback.startCell(context);
			callback.cellContent(context);
			callback.endCell(context);
		} else if (renderDimensionTitle) {
			final Map<Hierarchy, Integer> spans = new HashMap<Hierarchy, Integer>();

			rowRoot.walkChildrenAtColIndex(
					new TreeNodeCallback<TableAxisContext>() {

						@Override
						public int handleTreeNode(
								TreeNode<TableAxisContext> node) {
							TableHeaderNode headerNode = (TableHeaderNode) node;
							if (headerNode.getHierarchy() != null) {
								Integer span = spans.get(headerNode
										.getHierarchy());
								if (span == null) {
									span = 0;
								}

								span += headerNode.getRowSpan();
								spans.put(headerNode.getHierarchy(), span);
							}
							return TreeNodeCallback.CONTINUE;
						}
					}, 0);

			for (Hierarchy hierarchy : rowRoot.getReference().getHierarchies()) {
				int span = spans.get(hierarchy);

				context.setColSpan(span);
				context.setRowSpan(1);
				context.setHierarchy(hierarchy);
				context.setCellType(CellType.RowTitle);

				callback.startCell(context);
				callback.cellContent(context);
				callback.endCell(context);

				context.setColIndex(context.getColIndex() + span);
			}
		} else if (renderLevelTitle) {
			final Map<Integer, Level> levels = new HashMap<Integer, Level>();

			rowRoot.walkChildren(new TreeNodeCallback<TableAxisContext>() {

				@Override
				public int handleTreeNode(TreeNode<TableAxisContext> node) {
					TableHeaderNode headerNode = (TableHeaderNode) node;
					if (headerNode.getMember() != null) {
						int colIndex = headerNode.getRowIndex() - 1;

						if (!levels.containsKey(colIndex)) {
							levels.put(colIndex, headerNode.getMember()
									.getLevel());
						}
					}
					return TreeNodeCallback.CONTINUE;
				}
			});

			for (int i = 0; i < context.getRowHeaderCount(); i++) {
				context.setColSpan(1);
				context.setRowSpan(1);
				context.setColIndex(1);

				Level level = levels.get(i);

				if (level == null) {
					context.setHierarchy(null);
					context.setLevel(null);
				} else {
					context.setHierarchy(level.getHierarchy());
					context.setLevel(level);
				}
				context.setCellType(CellType.RowTitle);

				callback.startCell(context);
				callback.cellContent(context);
				callback.endCell(context);
			}
		}

		context.setHierarchy(null);
	}

	/**
	 * @param model
	 * @param axis
	 * @return
	 */
	protected TableHeaderNode createAxisTree(PivotModel model, Axis axis) {
		CellSetAxis cellSetAxis = model.getCellSet().getAxes()
				.get(axis.axisOrdinal());

		List<Position> positions = cellSetAxis.getPositions();
		if (positions == null || positions.isEmpty()) {
			return null;
		}

		List<Hierarchy> hierarchies = new ArrayList<Hierarchy>();
		Map<Hierarchy, List<Level>> levelsMap = new HashMap<Hierarchy, List<Level>>();

		TableAxisContext nodeContext = new TableAxisContext(axis, hierarchies,
				levelsMap);

		TableHeaderNode axisRoot = new TableHeaderNode(nodeContext);

		for (Position position : positions) {
			TableHeaderNode parentNode = axisRoot;

			List<Member> members = position.getMembers();

			for (Member member : members) {
				if (hierarchies.size() < members.size()) {
					hierarchies.add(member.getHierarchy());
				}

				List<Level> levels = levelsMap.get(member.getHierarchy());
				if (levels == null) {
					levels = new ArrayList<Level>();
					levelsMap.put(member.getHierarchy(), levels);
				}

				if (!levels.contains(member.getLevel())) {
					levels.add(member.getLevel());
				}

				TableHeaderNode childNode = new TableHeaderNode(nodeContext);
				childNode.setMember(member);
				childNode.setHierarchy(member.getHierarchy());
				childNode.setPosition(position);

				parentNode.addChild(childNode);
				parentNode = childNode;
			}
		}

		Comparator<Level> comparator = new Comparator<Level>() {

			@Override
			public int compare(Level l1, Level l2) {
				Integer d1 = l1.getDepth();
				Integer d2 = l2.getDepth();
				return d1.compareTo(d2);
			}
		};

		for (List<Level> levels : levelsMap.values()) {
			Collections.sort(levels, comparator);

		}

		return axisRoot;
	}

	/**
	 * @param model
	 * @param axis
	 * @param node
	 */
	protected void configureAxisTree(PivotModel model, Axis axis,
			TableHeaderNode node) {
		if (showDimensionTitle && axis.equals(Axis.COLUMNS)) {
			node.addHierarhcyHeaders();
		}

		if (showParentMembers) {
			node.addParentMemberHeaders();
		}

		if (!hideSpans) {
			node.mergeChildren();
		}
	}

	/**
	 * @param model
	 * @param axis
	 * @param node
	 */
	protected void freezeAxisTree(PivotModel model, Axis axis,
			TableHeaderNode node) {
		node.walkChildren(new TreeNodeCallback<TableAxisContext>() {

			@Override
			public int handleTreeNode(TreeNode<TableAxisContext> node) {
				TableHeaderNode headerNode = (TableHeaderNode) node;
				headerNode.setFreezed(true);
				return TreeNodeCallback.CONTINUE;
			}
		});
	}
}
