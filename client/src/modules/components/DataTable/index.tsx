/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Container, TableHeader, TableCell, TableRow} from './styled';
import {
  DataTable as CarbonDataTable,
  Table,
  TableHead,
  TableBody,
  TableContainer,
  TableExpandRow,
  TableExpandHeader,
  TableExpandedRow,
  DenormalizedRow,
} from '@carbon/react';

type Props = {
  size?: React.ComponentProps<typeof CarbonDataTable>['size'];
  headers: {key: string; header: string; width?: string}[];
  rows: React.ComponentProps<typeof CarbonDataTable>['rows'];
  className?: string;
  columnsWithNoContentPadding?: string[];
  isExpandable?: boolean;
  expandableRowTitle?: string;
  expandedContents?: {[key: string]: React.ReactNode};
  onRowClick?: (rowId: string) => void;
  checkIsRowSelected?: (rowId: string) => boolean;
};

const TableCells: React.FC<{
  row: DenormalizedRow;
  columnsWithNoContentPadding?: string[];
}> = ({row, columnsWithNoContentPadding}) => {
  return (
    <>
      {row.cells.map((cell) => (
        <TableCell
          key={cell.id}
          $hideCellPadding={columnsWithNoContentPadding?.includes(
            cell.info.header,
          )}
        >
          {cell.value}
        </TableCell>
      ))}
    </>
  );
};

const DataTable = React.forwardRef<HTMLDivElement, Props>(
  (
    {
      size = 'sm',
      headers,
      rows,
      className,
      columnsWithNoContentPadding,
      isExpandable,
      expandableRowTitle,
      expandedContents,
      onRowClick,
      checkIsRowSelected,
    },
    ref,
  ) => {
    return (
      <Container className={className} ref={ref}>
        <CarbonDataTable
          size={size}
          headers={headers}
          rows={rows}
          render={({
            rows,
            headers,
            getTableContainerProps,
            getTableProps,
            getRowProps,
          }) => (
            <TableContainer {...getTableContainerProps()}>
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    {isExpandable && <TableExpandHeader />}
                    {headers.map((header) => (
                      <TableHeader
                        id={header.key}
                        key={header.key}
                        $width={header.width}
                      >
                        {header.header}
                      </TableHeader>
                    ))}
                  </TableRow>
                </TableHead>

                <TableBody>
                  {rows.map((row) => {
                    if (isExpandable) {
                      const expandedContent = expandedContents?.[row.id];
                      return (
                        <React.Fragment key={row.id}>
                          <TableExpandRow
                            {...getRowProps({row})}
                            title={expandableRowTitle}
                            id={`expanded-row-${row.id}`}
                          >
                            <TableCells
                              row={row}
                              columnsWithNoContentPadding={
                                columnsWithNoContentPadding
                              }
                            />
                          </TableExpandRow>
                          <TableExpandedRow colSpan={headers.length + 1}>
                            {expandedContent}
                          </TableExpandedRow>
                        </React.Fragment>
                      );
                    }

                    const isSelected = checkIsRowSelected?.(row.id) ?? false;
                    const isClickable = onRowClick !== undefined;

                    return (
                      <TableRow
                        {...getRowProps({row})}
                        onClick={() => {
                          onRowClick?.(row.id);
                        }}
                        $isClickable={isClickable}
                        isSelected={isSelected}
                        aria-selected={isSelected}
                        tabIndex={isClickable ? 0 : undefined}
                        onKeyDown={({key}) => {
                          if (isClickable && key === 'Enter') {
                            onRowClick?.(row.id);
                          }
                        }}
                      >
                        <TableCells
                          row={row}
                          columnsWithNoContentPadding={
                            columnsWithNoContentPadding
                          }
                        />
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        />
      </Container>
    );
  },
);

export {DataTable};
