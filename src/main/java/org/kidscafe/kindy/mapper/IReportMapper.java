package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.ReportDTO;

import java.util.List;

/**
 * T_CHILD_REPORT is append-only, so there is no update and no delete here.
 *
 * A delete would have to mean "drop every version of this category", and the chat data cards
 * pointing at those versions are what the versioning exists for — the foreign key from
 * T_CHAT_MESSAGE.REPORT_ID refuses it anyway. The old {@code delete} had no caller and is gone.
 */
@Mapper
public interface IReportMapper {
    /** The newest version of each category — the child's current report card. */
    List<ReportDTO> selectList(ReportDTO pDTO) throws Exception;
    /** The newest version of one category. */
    ReportDTO select(ReportDTO pDTO) throws Exception;
    /** One named version, current or not. How a data card resolves what it was sent with. */
    ReportDTO selectById(long id) throws Exception;
    /** A new version. Writes the assigned ID back onto {@code pDTO}. */
    int insert(ReportDTO pDTO) throws Exception;
}
