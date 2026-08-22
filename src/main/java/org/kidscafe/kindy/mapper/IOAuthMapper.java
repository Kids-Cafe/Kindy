package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.OAuthDTO;

import java.util.List;

@Mapper
public interface IOAuthMapper {
    /** Which Kindy account, if any, a given provider identity logs in to. Keyed on (ID, PROVIDER). */
    OAuthDTO getLink(OAuthDTO pDTO) throws Exception;

    /** Every provider linked to one account, for the settings screen and the user profile. */
    List<OAuthDTO> getLinksByUser(OAuthDTO pDTO) throws Exception;

    int insertLink(OAuthDTO pDTO) throws Exception;

    /** Removes one provider from one account. Keyed on (USER_ID, PROVIDER). */
    int deleteLink(OAuthDTO pDTO) throws Exception;
}
