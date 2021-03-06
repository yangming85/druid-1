/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.segment;

import com.google.common.primitives.Ints;
import io.druid.data.input.impl.DimensionSchema.MultiValueHandling;
import io.druid.segment.column.Column;
import io.druid.segment.column.ColumnCapabilities;
import io.druid.segment.column.DictionaryEncodedColumn;
import io.druid.segment.data.IOPeon;
import io.druid.segment.data.Indexed;
import io.druid.segment.data.IndexedInts;

import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Array;
import java.util.Arrays;

public class StringDimensionHandler implements DimensionHandler<Integer, int[], String>
{
  private final String dimensionName;
  private final MultiValueHandling multiValueHandling;

  public StringDimensionHandler(String dimensionName, MultiValueHandling multiValueHandling)
  {
    this.dimensionName = dimensionName;
    this.multiValueHandling = multiValueHandling;
  }

  @Override
  public String getDimensionName()
  {
    return dimensionName;
  }

  @Override
  public int getLengthOfEncodedKeyComponent(int[] dimVals)
  {
    return dimVals.length;
  }

  @Override
  public int compareSortedEncodedKeyComponents(int[] lhs, int[] rhs)
  {
    int lhsLen = lhs.length;
    int rhsLen = rhs.length;

    int retVal = Ints.compare(lhsLen, rhsLen);

    int valsIndex = 0;
    while (retVal == 0 && valsIndex < lhsLen) {
      retVal = Ints.compare(lhs[valsIndex], rhs[valsIndex]);
      ++valsIndex;
    }
    return retVal;
  }

  @Override
  public void validateSortedEncodedKeyComponents(
      int[] lhs,
      int[] rhs,
      Indexed<String> lhsEncodings,
      Indexed<String> rhsEncodings
  ) throws SegmentValidationException
  {
    if (lhs == null || rhs == null) {
      if (lhs != null || rhs != null) {
        throw new SegmentValidationException(
            "Expected nulls, found %s and %s",
            Arrays.toString(lhs),
            Arrays.toString(rhs)
        );
      } else {
        return;
      }
    }

    int lhsLen = Array.getLength(lhs);
    int rhsLen = Array.getLength(rhs);

    if (lhsLen != rhsLen) {
      // Might be OK if one of them has null. This occurs in IndexMakerTest
      if (lhsLen == 0 && rhsLen == 1) {
        final String dimValName = rhsEncodings.get(rhs[0]);
        if (dimValName == null) {
          return;
        } else {
          throw new SegmentValidationException(
              "Dim [%s] value [%s] is not null",
              dimensionName,
              dimValName
          );
        }
      } else if (rhsLen == 0 && lhsLen == 1) {
        final String dimValName = lhsEncodings.get(lhs[0]);
        if (dimValName == null) {
          return;
        } else {
          throw new SegmentValidationException(
              "Dim [%s] value [%s] is not null",
              dimensionName,
              dimValName
          );
        }
      } else {
        throw new SegmentValidationException(
            "Dim [%s] value lengths not equal. Expected %d found %d",
            dimensionName,
            lhsLen,
            rhsLen
        );
      }
    }

    for (int j = 0; j < Math.max(lhsLen, rhsLen); ++j) {
      final int dIdex1 = lhsLen <= j ? -1 : lhs[j];
      final int dIdex2 = rhsLen <= j ? -1 : rhs[j];

      final String dim1ValName = dIdex1 < 0 ? null : lhsEncodings.get(dIdex1);
      final String dim2ValName = dIdex2 < 0 ? null : rhsEncodings.get(dIdex2);
      if ((dim1ValName == null) || (dim2ValName == null)) {
        if ((dim1ValName == null) && (dim2ValName == null)) {
          continue;
        } else {
          throw new SegmentValidationException(
              "Dim [%s] value not equal. Expected [%s] found [%s]",
              dimensionName,
              dim1ValName,
              dim2ValName
          );
        }
      }

      if (!dim1ValName.equals(dim2ValName)) {
        throw new SegmentValidationException(
            "Dim [%s] value not equal. Expected [%s] found [%s]",
            dimensionName,
            dim1ValName,
            dim2ValName
        );
      }
    }
  }

  @Override
  public Closeable getSubColumn(Column column)
  {
    return column.getDictionaryEncoding();
  }

  @Override
  public int[] getEncodedKeyComponentFromColumn(Closeable column, int currRow)
  {
    DictionaryEncodedColumn dict = (DictionaryEncodedColumn) column;
    int[] theVals;
    if (dict.hasMultipleValues()) {
      final IndexedInts dimVals = dict.getMultiValueRow(currRow);
      theVals = new int[dimVals.size()];
      for (int i = 0; i < theVals.length; ++i) {
        theVals[i] = dimVals.get(i);
      }
    } else {
      theVals = new int[1];
      theVals[0] = dict.getSingleValueRow(currRow);
    }

    return theVals;
  }

  @Override
  public DimensionIndexer<Integer, int[], String> makeIndexer()
  {
    return new StringDimensionIndexer(multiValueHandling);
  }

  @Override
  public DimensionMergerV9 makeMerger(
      IndexSpec indexSpec,
      File outDir,
      IOPeon ioPeon,
      ColumnCapabilities capabilities,
      ProgressIndicator progress
  )
  {
    return new StringDimensionMergerV9(dimensionName, indexSpec, outDir, ioPeon, capabilities, progress);
  }

  @Override
  public DimensionMergerLegacy makeLegacyMerger(
      IndexSpec indexSpec,
      File outDir,
      IOPeon ioPeon,
      ColumnCapabilities capabilities,
      ProgressIndicator progress
  )
  {
    return new StringDimensionMergerLegacy(dimensionName, indexSpec, outDir, ioPeon, capabilities, progress);
  }
}
